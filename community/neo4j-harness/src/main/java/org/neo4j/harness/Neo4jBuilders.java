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
package org.neo4j.harness;

import java.io.File;
import java.nio.file.Path;

import org.neo4j.annotations.api.PublicApi;
import org.neo4j.harness.internal.InProcessNeo4jBuilder;

/**
 * Factories for creating {@link Neo4jBuilder} instances.
 */
@PublicApi
public final class Neo4jBuilders
{
    /**
     * Create a builder capable of starting an in-process Neo4j instance. This builder will use the standard java temp
     * directory (configured via the 'java.io.tmpdir' system property) as the location for the temporary Neo4j directory.
     */
    public static Neo4jBuilder newInProcessBuilder()
    {
        return new InProcessNeo4jBuilder();
    }

    /**
     * Create a builder capable of starting an in-process Neo4j instance, running in a subdirectory of the specified directory.
     * @deprecated Use {@link #newInProcessBuilder(Path)}.
     */
    @Deprecated( forRemoval = true )
    public static Neo4jBuilder newInProcessBuilder( File workingDirectory )
    {
        return newInProcessBuilder( workingDirectory.toPath() );
    }

    /**
     * Create a builder capable of starting an in-process Neo4j instance, running in a subdirectory of the specified directory.
     */
    public static Neo4jBuilder newInProcessBuilder( Path workingDirectory )
    {
        return new InProcessNeo4jBuilder( workingDirectory );
    }

    private Neo4jBuilders() {}
}
