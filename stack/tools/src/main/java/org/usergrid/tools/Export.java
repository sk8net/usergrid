/*******************************************************************************
 * Copyright 2012 Apigee Corporation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.usergrid.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usergrid.management.OrganizationInfo;
import org.usergrid.persistence.ConnectionRef;
import org.usergrid.persistence.Entity;
import org.usergrid.persistence.EntityManager;
import org.usergrid.persistence.Results;
import org.usergrid.persistence.Results.Level;
import org.usergrid.utils.JsonUtils;

import com.google.common.collect.BiMap;

public class Export extends ExportingToolBase {

	static final Logger logger = LoggerFactory.getLogger(Export.class);

	JsonFactory jsonFactory = new JsonFactory();


	@Override
	public void runTool(CommandLine line) throws Exception {
		startSpring();

		setVerbose(line);

		prepareBaseOutputFileName(line);
		outputDir = createOutputParentDir();
		logger.info("Export directory: " + outputDir.getAbsolutePath());

		// Export organizations separately.
		exportOrganizations();

		// Loop through the organizations
		BiMap<UUID, String> organizations = managementService
				.getOrganizations();
		for (Entry<UUID, String> organization : organizations.entrySet()) {

			if (organization.equals(properties
					.getProperty("usergrid.test-account.organization"))) {
				// Skip test data from being exported.
				continue;
			}

			exportApplicationsForOrg(organization);
		}
	}

	private void exportApplicationsForOrg(Entry<UUID, String> organization)
			throws Exception {
		logger.info("" + organization);

		// Loop through the applications per organization
		BiMap<UUID, String> applications = managementService
				.getApplicationsForOrganization(organization.getKey());
		for (Entry<UUID, String> application : applications.entrySet()) {

			logger.info(application.getValue() + " : " + application.getKey());

			// Get the JSon serializer.
			JsonGenerator jg = getJsonGenerator(createOutputFile("application",
					application.getValue()));

			EntityManager em = emf.getEntityManager(application.getKey());

			// Write application
			Entity nsEntity = em.get(application.getKey());
			nsEntity.setMetadata("organization", organization);
			jg.writeStartArray();
			jg.writeObject(nsEntity);

			// Create a generator for the application collections.
			JsonGenerator collectionsJg = getJsonGenerator(createOutputFile(
					"collections", application.getValue()));
			collectionsJg.writeStartObject();

			Map<String, Object> metadata = em
					.getApplicationCollectionMetadata();
			echo(JsonUtils.mapToFormattedJsonString(metadata));

			// Loop through the collections. This is the only way to loop
			// through the entities in the application (former namespace).
			for (String collectionName : metadata.keySet()) {
				Results r = em.getCollection(em.getApplicationRef(),
						collectionName, null, 100000, Results.Level.IDS, false);

				echo(r.size() + " entity ids loaded");
				int size = r.size();

				for (int i = 0; i < size; i += MAX_ENTITY_FETCH) {

					// Batch the read to avoid big amount of data
					int finish = Math.min(i + MAX_ENTITY_FETCH, size);
					List<UUID> entityIds = r.getIds().subList(i, finish);

					logger.info("Retrieving entities " + i + " through "
							+ (finish - 1) + " Found:" + entityIds.size());

					Results entities = em.get(entityIds,
							Results.Level.ALL_PROPERTIES);

					for (Entity entity : entities) {
						// Export the entity first and later the collections for
						// this entity.
						jg.writeObject(entity);
						echo(entity);

						saveCollectionMembers(collectionsJg, em,
								application.getValue(), entity);
					}
				}
			}

			// Close writer for the collections for this application.
			collectionsJg.writeEndObject();
			collectionsJg.close();

			// Close writer and file for this application.
			jg.writeEndArray();
			jg.close();
		}

	}

	/**
	 * Serialize and save the collection members of this <code>entity</code>
	 *
	 * @param em
	 *            Entity Manager
	 * @param application
	 *            Application name
	 * @param entity
	 *            entity
	 * @throws Exception
	 */
	private void saveCollectionMembers(JsonGenerator jg, EntityManager em,
			String application, Entity entity) throws Exception {

		Set<String> collections = em.getCollections(entity);

		// Only create entry for Entities that have collections
		if ((collections == null) || collections.isEmpty()) {
			return;
		}

		jg.writeFieldName(entity.getUuid().toString());
		jg.writeStartObject();

		for (String collectionName : collections) {

			jg.writeFieldName(collectionName);
			// Start collection array.
			jg.writeStartArray();

			Results collectionMembers = em.getCollection(entity,
					collectionName, null, 100000, Level.IDS, false);

			List<UUID> entityIds = collectionMembers.getIds();

			if ((entityIds != null) && !entityIds.isEmpty()) {
				for (UUID childEntityUUID : entityIds) {
					jg.writeObject(childEntityUUID.toString());
				}
			}

			// End collection array.
			jg.writeEndArray();
		}

		// Write collections
		if ((collections != null) && !collections.isEmpty()) {
			saveConnections(entity, em, jg);
		}

		// End the object if it was Started
		jg.writeEndObject();
	}

	/**
	 * Persists the connection for this entity.
	 */
	private void saveConnections(Entity entity, EntityManager em,
			JsonGenerator jg) throws Exception {

		jg.writeFieldName("connections");
		jg.writeStartObject();

		Set<String> connectionTypes = em.getConnectionTypes(entity);
		for (String connectionType : connectionTypes) {

			jg.writeFieldName(connectionType);
			jg.writeStartArray();

			Results results = em.getConnectedEntities(entity.getUuid(),
					connectionType, null, Level.IDS);
			List<ConnectionRef> connections = results.getConnections();

			for (ConnectionRef connectionRef : connections) {
				jg.writeObject(connectionRef.getConnectedEntity().getUuid());
			}

			jg.writeEndArray();
		}
		jg.writeEndObject();
	}

	/*-
	 * Set<String> collections = em.getCollections(entity);
	 * for (String collection : collections) {
	 *   Results collectionMembers = em.getCollection(
	 *    entity, collection, null,
	 *    MAX_ENTITY_FETCH, Level.IDS, false);
	 *    write entity_id : { "collectionName" : [ids]
	 *  }
	 * }
	 * 
	 * 
	 *   {
	 *     entity_id :
	 *       { collection_name :
	 *         [
	 *           collected_entity_id,
	 *           collected_entity_id
	 *         ]
	 *       },
	 *     f47ac10b-58cc-4372-a567-0e02b2c3d479 :
	 *       { "activtites" :
	 *         [
	 *           f47ac10b-58cc-4372-a567-0e02b2c3d47A,
	 *           f47ac10b-58cc-4372-a567-0e02b2c3d47B
	 *         ]
	 *       }
	 *   }
	 * 
	 * http://jackson.codehaus.org/1.8.0/javadoc/org/codehaus/jackson/JsonGenerator.html
	 * 
	 *
	 *-
	 * List<ConnectedEntityRef> connections = em.getConnections(entityId, query);
	 */



	private void exportOrganizations() throws Exception,
			UnsupportedEncodingException {
		// Loop through the organizations
		BiMap<UUID, String> organizationNames = managementService
				.getOrganizations();
		for (Entry<UUID, String> organizationName : organizationNames
				.entrySet()) {

			// Let's skip the test entities.
			if (organizationName.equals(properties
					.getProperty("usergrid.test-account.organization"))) {
				continue;
			}

			OrganizationInfo acc = managementService
					.getOrganizationByUuid(organizationName.getKey());
			logger.info("Exporting Organization: " + acc.getName());

			// One file per Organization.
			saveOrganizationInFile(acc);
		}

	}

	/**
	 * Serialize an Organization into a json file.
	 * 
	 * @param acc
	 *            OrganizationInfo
	 */
	private void saveOrganizationInFile(OrganizationInfo acc) {
		try {
			File outFile = createOutputFile("organization", acc.getName());
			JsonGenerator jg = getJsonGenerator(outFile);
			jg.writeObject(acc);
			jg.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}



	public void streamOutput(File file, List<Entity> entities) throws Exception {
		JsonFactory jsonFactory = new JsonFactory();
		// or, for data binding,
		// org.codehaus.jackson.mapper.MappingJsonFactory
		JsonGenerator jg = jsonFactory.createJsonGenerator(file,
				JsonEncoding.UTF8);
		// or Stream, Reader

		jg.writeStartArray();
		for (Entity entity : entities) {
			jg.writeObject(entity);

		}
		jg.writeEndArray();

		jg.close();
	}

	// to generate the activities and user relationship, follow this:

	// write field name (id)
	// write start object
	// write field name (collection name)
	// write start array
	// write object/string
	// write another object
	// write end array
	// write end object
	// ...... more objects
	//

}
