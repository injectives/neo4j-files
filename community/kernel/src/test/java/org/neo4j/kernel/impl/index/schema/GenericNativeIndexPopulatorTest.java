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
package org.neo4j.kernel.impl.index.schema;

import org.junit.jupiter.api.Test;

import java.io.File;

import org.neo4j.gis.spatial.index.curves.SpaceFillingCurveConfiguration;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.IndexPrototype;
import org.neo4j.internal.schema.SchemaDescriptor;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.kernel.api.index.IndexDirectoryStructure;
import org.neo4j.kernel.impl.index.schema.config.IndexSpecificSpaceFillingCurveSettings;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.pagecache.PageCacheExtension;
import org.neo4j.test.rule.TestDirectory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.neo4j.kernel.api.index.IndexProvider.Monitor.EMPTY;
import static org.neo4j.kernel.api.schema.SchemaTestUtil.SIMPLE_NAME_LOOKUP;

@PageCacheExtension
class GenericNativeIndexPopulatorTest
{
    @Inject
    private PageCache pageCache;
    @Inject
    private TestDirectory testDirectory;
    @Inject
    private FileSystemAbstraction fs;

    @Test
    void dropShouldDeleteEntireIndexFolder()
    {
        // given
        File root = testDirectory.directory( "root" );
        IndexDirectoryStructure directoryStructure = IndexDirectoryStructure.directoriesByProvider( root ).forProvider( GenericNativeIndexProvider.DESCRIPTOR );
        long indexId = 8;
        File indexDirectory = directoryStructure.directoryForIndex( indexId );
        IndexDescriptor descriptor = IndexPrototype.forSchema( SchemaDescriptor.forLabel( 1, 1 ) ).withName( "index" ).materialise( indexId );
        IndexSpecificSpaceFillingCurveSettings spatialSettings = mock( IndexSpecificSpaceFillingCurveSettings.class );
        IndexFiles.Directory indexFiles = new IndexFiles.Directory( fs, directoryStructure, indexId );
        GenericLayout layout = new GenericLayout( 1, spatialSettings );
        GenericNativeIndexPopulator populator = new GenericNativeIndexPopulator( pageCache, fs, indexFiles, layout,
                EMPTY, descriptor, spatialSettings, mock( SpaceFillingCurveConfiguration.class ), false, SIMPLE_NAME_LOOKUP );
        populator.create();

        // when
        assertTrue( fs.listFiles( indexDirectory ).length > 0 );
        populator.drop();

        // then
        assertFalse( fs.fileExists( indexDirectory ) );
    }
}