/**
 * Copyright (c) 2002-2010 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.neo4j.server.plugins;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.server.NeoServer;
import org.neo4j.server.ServerBuilder;
import org.neo4j.server.rest.FunctionalTestHelper;
import org.neo4j.server.rest.domain.JsonHelper;
import org.neo4j.server.rest.domain.JsonParseException;
import org.neo4j.server.rest.repr.NodeRepresentationTest;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class ReferenceNodeFunctionalTest
{
    private NeoServer server;
    private FunctionalTestHelper functionalTestHelper;

    @Before
    public void setupServer() throws Exception
    {
        server = ServerBuilder.server().withRandomDatabaseDir().withPassingStartupHealthcheck().build();
        server.start();
        functionalTestHelper = new FunctionalTestHelper( server );
    }

    @After
    public void stopServer()
    {
        if ( server != null )
        {
            server.stop();
        }
    }

    @Test
    public void canGetGraphDatabaseExtensionList() throws Exception
    {
        Map<String, Object> map = makeGet( functionalTestHelper.dataUri() );
        assertThat( map.get( "extensions" ), instanceOf( Map.class ) );
    }

    @Test
    public void canGetExtensionDefinitionForReferenceNodeExtension() throws Exception
    {
        Map<String, Object> map = makeGet( functionalTestHelper.dataUri() );
        map = (Map<String, Object>)map.get( "extensions" );

        assertThat( map.get( ReferenceNode.class.getSimpleName() ), instanceOf( Map.class ) );
    }

    @Test
    public void canGetExtensionDataForGetReferenceNode() throws Exception
    {
        Map<String, Object> map = makeGet( functionalTestHelper.dataUri() );
        map = (Map<String, Object>)map.get( "extensions" );
        map = (Map<String, Object>)map.get( ReferenceNode.class.getSimpleName() );

        assertThat( (String)map.get( ReferenceNode.GET_REFERENCE_NODE ),
                RegExp.endsWith( String.format( "/ext/%s/graphdb/%s",
                        ReferenceNode.class.getSimpleName(), ReferenceNode.GET_REFERENCE_NODE ) ) );
    }

    @Test
    public void canGetExtensionDescription() throws Exception
    {
        Map<String, Object> map = makeGet( functionalTestHelper.dataUri() );
        map = (Map<String, Object>)map.get( "extensions" );
        map = (Map<String, Object>)map.get( ReferenceNode.class.getSimpleName() );

        String uri = (String)map.get( ReferenceNode.GET_REFERENCE_NODE );
        makeGet( uri );
    }

    @Test
    public void canInvokeExtensionMethodWithNoArguments() throws Exception
    {
        Map<String, Object> map = makeGet( functionalTestHelper.dataUri() );
        map = (Map<String, Object>)map.get( "extensions" );
        map = (Map<String, Object>)map.get( ReferenceNode.class.getSimpleName() );

        String uri = (String)map.get( ReferenceNode.GET_REFERENCE_NODE );
        Map<String, Object> description = makePostMap( uri );

        NodeRepresentationTest.verifySerialisation( description );
    }

    @Test
    public void canInvokeNodePlugin() throws Exception
    {
        long n = functionalTestHelper.getGraphDbHelper().createNode();

        Map<String, Object> map = makeGet( functionalTestHelper.nodeUri( n ) );
        map = (Map<String, Object>)map.get( "extensions" );
        map = (Map<String, Object>)map.get( ReferenceNode.class.getSimpleName() );

        String uri = (String)map.get( ReferenceNode.GET_CONNECTED_NODES );
        List<Map<String, Object>> response = makePostList( uri );
        for( Map<String, Object> nodeMap : response)
        {
            NodeRepresentationTest.verifySerialisation( nodeMap );
        }
    }


    private Map<String, Object> makeGet( String url ) throws JsonParseException
    {
        ClientResponse response = Client.create().resource( url ).accept(
                MediaType.APPLICATION_JSON_TYPE ).get( ClientResponse.class );

        String body = getResponseText( response );

        return deserializeMap( body );
    }

    private Map<String, Object> deserializeMap( final String body )
            throws JsonParseException
    {
        Map<String, Object> result = JsonHelper.jsonToMap( body );
        assertThat( result, is( not( nullValue() ) ) );
        return result;
    }

    private List<Map<String, Object>> deserializeList( final String body )
            throws JsonParseException
    {
        List<Map<String, Object>> result = JsonHelper.jsonToList( body );
        assertThat( result, is( not( nullValue() ) ) );
        return result;
    }

    private String getResponseText( final ClientResponse response )
    {
        String body = response.getEntity( String.class );

        assertEquals( 200, response.getStatus() );
        return body;
    }

    private Map<String, Object> makePostMap( String url ) throws JsonParseException
    {
        ClientResponse response = Client.create().resource( url ).accept(
                MediaType.APPLICATION_JSON_TYPE ).post( ClientResponse.class );

        String body = getResponseText( response );

        return deserializeMap( body );
    }

    private List<Map<String, Object>> makePostList( String url ) throws JsonParseException
    {
        ClientResponse response = Client.create().resource( url ).accept(
                MediaType.APPLICATION_JSON_TYPE ).post( ClientResponse.class );

        String body = getResponseText( response );

        return deserializeList( body );
    }


    private static class RegExp extends TypeSafeMatcher<String>
    {
        enum MatchType
        {
            end( "ends with" )
                    {
                        @Override
                        boolean match( String pattern, String string )
                        {
                            return string.endsWith( pattern );
                        }
                    },
            matches()
                    {
                        @Override
                        boolean match( String pattern, String string )
                        {
                            return string.matches( pattern );
                        }
                    },;
            private final String description;

            abstract boolean match( String pattern, String string );

            private MatchType()
            {
                this.description = name();
            }

            private MatchType( String description )
            {
                this.description = description;
            }
        }

        private final String pattern;
        private String string;
        private final MatchType type;

        RegExp( String regexp, MatchType type )
        {
            this.pattern = regexp;
            this.type = type;
        }

        @Factory
        public static Matcher<String> endsWith( String pattern )
        {
            return new RegExp( pattern, MatchType.end );
        }

        @Override
        public boolean matchesSafely( String string )
        {
            this.string = string;
            return type.match( pattern, string );
        }

        @Override
        public void describeTo( Description descr )
        {
            descr.appendText( "expected something that " ).appendText( type.description ).appendText(
                    " [" ).appendText( pattern ).appendText( "] but got [" ).appendText( string ).appendText(
                    "]" );
        }
    }
}
