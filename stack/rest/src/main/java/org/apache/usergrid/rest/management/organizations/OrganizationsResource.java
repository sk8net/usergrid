/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.usergrid.rest.management.organizations;


import java.util.Map;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.apache.usergrid.management.ApplicationCreator;
import org.apache.usergrid.management.OrganizationInfo;
import org.apache.usergrid.management.OrganizationOwnerInfo;
import org.apache.usergrid.management.exceptions.ManagementException;
import org.apache.usergrid.rest.AbstractContextResource;
import org.apache.usergrid.rest.ApiResponse;
import org.apache.usergrid.rest.security.annotations.RequireOrganizationAccess;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.Preconditions;
import com.sun.jersey.api.json.JSONWithPadding;


@Component( "org.apache.usergrid.rest.management.organizations.OrganizationsResource" )
@Scope( "prototype" )
@Produces( {
        MediaType.APPLICATION_JSON, "application/javascript", "application/x-javascript", "text/ecmascript",
        "application/ecmascript", "text/jscript"
} )
public class OrganizationsResource extends AbstractContextResource {

    private static final Logger logger = LoggerFactory.getLogger( OrganizationsResource.class );

    public static final String ORGANIZATION_PROPERTIES = "properties";

    @Autowired
    private ApplicationCreator applicationCreator;


    public OrganizationsResource() {
    }


    @Path( "{organizationId: [A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}}" )
    @RequireOrganizationAccess
    public OrganizationResource getOrganizationById( @Context UriInfo ui,
                                                     @PathParam( "organizationId" ) String organizationIdStr )
            throws Exception {
        OrganizationInfo organization = management.getOrganizationByUuid( UUID.fromString( organizationIdStr ) );
        if ( organization == null ) {
            throw new ManagementException( "Could not find organization for ID: " + organizationIdStr );
        }
        return getSubResource( OrganizationResource.class ).init( organization );
    }


    @Path( "{organizationName}" )
    @RequireOrganizationAccess
    public OrganizationResource getOrganizationByName( @Context UriInfo ui,
                                                       @PathParam( "organizationName" ) String organizationName )
            throws Exception {
        OrganizationInfo organization = management.getOrganizationByName( organizationName );
        if ( organization == null ) {
            throw new ManagementException( "Could not find organization for name: " + organizationName );
        }
        return getSubResource( OrganizationResource.class ).init( organization );
    }


    @POST
    @Consumes( MediaType.APPLICATION_JSON )
    public JSONWithPadding newOrganization( @Context UriInfo ui, Map<String, Object> json,
                                            @QueryParam( "callback" ) @DefaultValue( "" ) String callback )
            throws Exception {
        ApiResponse response = createApiResponse();
        response.setAction( "new organization" );

        String organizationName = ( String ) json.remove( "organization" );
        String username = ( String ) json.remove( "username" );
        String name = ( String ) json.remove( "name" );
        String email = ( String ) json.remove( "email" );
        String password = ( String ) json.remove( "password" );
        Map<String, Object> properties = ( Map<String, Object> ) json.remove( ORGANIZATION_PROPERTIES );

        return newOrganization( ui, organizationName, username, name, email, password, json, properties, callback );
    }


    @POST
    @Consumes( MediaType.APPLICATION_FORM_URLENCODED )
    public JSONWithPadding newOrganizationFromForm( @Context UriInfo ui,
                                                    @FormParam( "organization" ) String organizationNameForm,
                                                    @QueryParam( "organization" ) String organizationNameQuery,
                                                    @FormParam( "username" ) String usernameForm,
                                                    @QueryParam( "username" ) String usernameQuery,
                                                    @FormParam( "name" ) String nameForm,
                                                    @QueryParam( "name" ) String nameQuery,
                                                    @FormParam( "email" ) String emailForm,
                                                    @QueryParam( "email" ) String emailQuery,
                                                    @FormParam( "password" ) String passwordForm,
                                                    @QueryParam( "password" ) String passwordQuery,
                                                    @QueryParam( "callback" ) @DefaultValue( "" ) String callback )
            throws Exception {

        String organizationName = organizationNameForm != null ? organizationNameForm : organizationNameQuery;
        String username = usernameForm != null ? usernameForm : usernameQuery;
        String name = nameForm != null ? nameForm : nameQuery;
        String email = emailForm != null ? emailForm : emailQuery;
        String password = passwordForm != null ? passwordForm : passwordQuery;

        return newOrganization( ui, organizationName, username, name, email, password, null, null, callback );
    }


    /** Create a new organization */
    private JSONWithPadding newOrganization( @Context UriInfo ui, String organizationName, String username, String name,
                                             String email, String password, Map<String, Object> userProperties,
                                             Map<String, Object> properties, String callback ) throws Exception {
        Preconditions
                .checkArgument( StringUtils.isNotBlank( organizationName ), "The organization parameter was missing" );

        logger.info( "New organization: {}", organizationName );

        ApiResponse response = createApiResponse();
        response.setAction( "new organization" );

        OrganizationOwnerInfo organizationOwner = management
                .createOwnerAndOrganization( organizationName, username, name, email, password, false, false,
                        userProperties, properties );

        if ( organizationOwner == null ) {
            logger.info( "organizationOwner is null, returning. organization: {}", organizationName );
            return null;
        }

        applicationCreator.createSampleFor( organizationOwner.getOrganization() );

        response.setData( organizationOwner );
        response.setSuccess();

        logger.info( "New organization complete: {}", organizationName );
        return new JSONWithPadding( response, callback );
    }

    /*
     * @POST
     * 
     * @Consumes(MediaType.MULTIPART_FORM_DATA) public JSONWithPadding
     * newOrganizationFromMultipart(@Context UriInfo ui,
     * 
     * @FormDataParam("organization") String organization,
     * 
     * @FormDataParam("username") String username,
     * 
     * @FormDataParam("name") String name,
     * 
     * @FormDataParam("email") String email,
     * 
     * @FormDataParam("password") String password) throws Exception { return
     * newOrganizationFromForm(ui, organization, username, name, email,
     * password); }
     */
}
