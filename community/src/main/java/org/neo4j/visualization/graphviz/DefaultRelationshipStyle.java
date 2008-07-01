/*
 * Copyright 2008 Network Engine for Objects in Lund AB [neotechnology.com]
 * 
 * This program is free software: you can redistribute it and/or modify
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.visualization.graphviz;

import java.io.IOException;

import org.neo4j.api.core.Node;
import org.neo4j.api.core.Relationship;
import org.neo4j.visualization.PropertyType;

class DefaultRelationshipStyle implements RelationshipStyle
{
	private final DefaultStyleConfiguration config;

	DefaultRelationshipStyle( DefaultStyleConfiguration configuration )
	{
		this.config = configuration;
	}

	public void emitRelationshipStart( Appendable stream,
	    Relationship relationship ) throws IOException
	{
		Node start = relationship.getStartNode(), end = relationship
		    .getEndNode();
		stream.append( "  N" + start.getId() + " -> N" + end.getId() + " [\n" );
		config.emit( relationship, stream );
		if ( config.displayRelationshipLabel )
		{
			stream.append( "    label = \"" + config.getTitle( relationship )
			    + "\\n" );
		}
	}

	public void emitEnd( Appendable stream ) throws IOException
	{
		stream.append( config.displayRelationshipLabel ? "\"\n  ]\n" : "  ]\n" );
	}

	public void emitProperty( Appendable stream, String key, Object value )
	    throws IOException
	{
		if ( config.displayRelationshipLabel && config.acceptEdgeProperty( key ) )
		{
			PropertyType type = PropertyType.getTypeOf( value );
			config.emitRelationshipProperty( stream, key, type, value );
		}
	}
}
