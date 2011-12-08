package org.usergrid.android.client.entities;

import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;
import static org.usergrid.android.client.Utils.getStringProperty;
import static org.usergrid.android.client.Utils.setStringProperty;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.annotate.JsonSerialize;

public class Group extends Entity {

	public final static String ENTITY_TYPE = "group";

	public final static String PROPERTY_PATH = "path";
	public final static String PROPERTY_TITLE = "title";

	public Group() {
		super();
		setType(ENTITY_TYPE);
	}

	public Group(Entity entity) {
		super();
		properties = entity.properties;
		setType(ENTITY_TYPE);
	}

	@Override
	@JsonIgnore
	public String getNativeType() {
		return ENTITY_TYPE;
	}

	@Override
	@JsonIgnore
	public List<String> getPropertyNames() {
		List<String> properties = super.getPropertyNames();
		properties.add(PROPERTY_PATH);
		properties.add(PROPERTY_TITLE);
		return properties;
	}

	@JsonSerialize(include = NON_NULL)
	public String getPath() {
		return getStringProperty(properties, PROPERTY_PATH);
	}

	public void setPath(String path) {
		setStringProperty(properties, PROPERTY_PATH, path);
	}

	@JsonSerialize(include = NON_NULL)
	public String getTitle() {
		return getStringProperty(properties, PROPERTY_TITLE);
	}

	public void setTitle(String title) {
		setStringProperty(properties, PROPERTY_TITLE, title);
	}

}
