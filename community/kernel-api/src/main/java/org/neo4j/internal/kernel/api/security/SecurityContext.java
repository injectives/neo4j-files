/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.internal.kernel.api.security;

import java.util.Collections;
import java.util.Set;

import org.neo4j.graphdb.security.AuthorizationViolationException;
import org.neo4j.internal.kernel.api.connectioninfo.ClientConnectionInfo;
import org.neo4j.kernel.api.exceptions.Status;

import static org.neo4j.graphdb.security.AuthorizationViolationException.PERMISSION_DENIED;
import static org.neo4j.internal.kernel.api.connectioninfo.ClientConnectionInfo.EMBEDDED_CONNECTION;

/**
 * Controls the capabilities of a KernelTransaction, including the authenticated user and authorization data.
 *
 * Must extend LoginContext to handle procedures creating internal transactions, periodic commit and the parallel cypher prototype.
 */
public class SecurityContext extends LoginContext
{
    protected final AccessMode mode;

    public SecurityContext( AuthSubject subject, AccessMode mode, ClientConnectionInfo connectionInfo )
    {
        super( subject, connectionInfo );
        this.mode = mode;
    }

    /**
     * Get the authorization data of the user. This is immutable.
     */
    public AccessMode mode()
    {
        return mode;
    }

    /**
     * Check whether the user is allowed to execute procedure annotated with @Admin.
     */
    public boolean allowExecuteAdminProcedure( int procedureId )
    {
        return true;
    }

    /**
     * Check whether the user has a specific level of admin rights.
     */
    public boolean allowsAdminAction( AdminActionOnResource action )
    {
        assertCredentialsNotExpired();
        return true;
    }

    public Set<String> roles()
    {
        return Collections.emptySet();
    }

    @Override
    public SecurityContext authorize( IdLookup idLookup, String dbName )
    {
        return this;
    }

    /**
     * Create a copy of this SecurityContext with the provided mode.
     */
    public SecurityContext withMode( AccessMode mode )
    {
        return new SecurityContext( subject, mode, connectionInfo() );
    }

    /**
     * Create a copy of this SecurityContext with the provided admin access mode.
     */
    public SecurityContext withMode( AdminAccessMode adminAccessMode )
    {
        return new SecurityContext( subject, mode, connectionInfo() );
    }

    public void assertCredentialsNotExpired()
    {
        if ( AuthenticationResult.PASSWORD_CHANGE_REQUIRED.equals( subject().getAuthenticationResult() ) )
        {
            throw new AuthorizationViolationException( SecurityAuthorizationHandler.generateCredentialsExpiredMessage( PERMISSION_DENIED ),
                                                       Status.Security.CredentialsExpired );
        }
    }

    public String description()
    {
        return String.format( "user '%s' with %s", subject().username(), mode().name() );
    }

    protected String defaultString( String name )
    {
        return String.format( "%s{ username=%s, accessMode=%s }", name, subject().username(), mode() );
    }

    /** Allows all operations. */
    public static final SecurityContext AUTH_DISABLED = authDisabled( AccessMode.Static.FULL, EMBEDDED_CONNECTION );

    public static SecurityContext authDisabled( AccessMode mode, ClientConnectionInfo connectionInfo )
    {
        return new SecurityContext( AuthSubject.AUTH_DISABLED, mode, connectionInfo )
        {
            @Override
            public SecurityContext withMode( AccessMode mode )
            {
                return authDisabled( mode, connectionInfo() );
            }

            @Override
            public String description()
            {
                return "AUTH_DISABLED with " + mode().name();
            }

            @Override
            public String toString()
            {
                return defaultString( "auth-disabled" );
            }
        };
    }
}
