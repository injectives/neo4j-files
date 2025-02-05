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
package org.neo4j.batchinsert.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.neo4j.batchinsert.BatchInserter;
import org.neo4j.batchinsert.BatchInserters;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.counts.CountsAccessor;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.index.internal.gbptree.RecoveryCleanupWorkCollector;
import org.neo4j.internal.counts.CountsBuilder;
import org.neo4j.internal.counts.GBPTreeCountsStore;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.internal.helpers.collection.Pair;
import org.neo4j.internal.schema.IndexProviderDescriptor;
import org.neo4j.internal.schema.SchemaDescriptor;
import org.neo4j.internal.schema.SchemaDescriptors;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.recordstorage.RecordDatabaseLayout;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.kernel.extension.ExtensionFactory;
import org.neo4j.kernel.impl.MyRelTypes;
import org.neo4j.kernel.impl.coreapi.TransactionImpl;
import org.neo4j.kernel.impl.store.NeoStores;
import org.neo4j.kernel.impl.store.NodeLabels;
import org.neo4j.kernel.impl.store.NodeLabelsField;
import org.neo4j.kernel.impl.store.NodeStore;
import org.neo4j.kernel.impl.store.record.NodeRecord;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.NullLogProvider;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.cursor.StoreCursors;
import org.neo4j.storageengine.api.TransactionIdStore;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.Neo4jLayoutExtension;
import org.neo4j.test.extension.pagecache.PageCacheExtension;
import org.neo4j.test.utils.TestDirectory;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;
import static org.neo4j.configuration.GraphDatabaseSettings.dense_node_threshold;
import static org.neo4j.configuration.GraphDatabaseSettings.neo4j_home;
import static org.neo4j.configuration.GraphDatabaseSettings.preallocate_logical_logs;
import static org.neo4j.dbms.database.readonly.DatabaseReadOnlyChecker.readOnly;
import static org.neo4j.graphdb.Label.label;
import static org.neo4j.internal.helpers.collection.Iterables.map;
import static org.neo4j.internal.helpers.collection.Iterators.asSet;
import static org.neo4j.internal.helpers.collection.MapUtil.map;
import static org.neo4j.io.pagecache.context.CursorContext.NULL;
import static org.neo4j.kernel.impl.store.record.RecordLoad.NORMAL;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

@PageCacheExtension
@Neo4jLayoutExtension
class BatchInsertTest
{
    private static final String INTERNAL_LOG_FILE = "debug.log";
    private static final Map<String, Object> properties = new HashMap<>();
    private static final RelationshipType[] relTypeArray = {
        RelTypes.REL_TYPE1, RelTypes.REL_TYPE2, RelTypes.REL_TYPE3,
        RelTypes.REL_TYPE4, RelTypes.REL_TYPE5 };

    static
    {
        properties.put( "key0", "SDSDASSDLKSDSAKLSLDAKSLKDLSDAKLDSLA" );
        properties.put( "key1", 1 );
        properties.put( "key2", (short) 2 );
        properties.put( "key3", 3L );
        properties.put( "key4", 4.0f );
        properties.put( "key5", 5.0d );
        properties.put( "key6", (byte) 6 );
        properties.put( "key7", true );
        properties.put( "key8", (char) 8 );
        properties.put( "key10", new String[]{
            "SDSDASSDLKSDSAKLSLDAKSLKDLSDAKLDSLA", "dsasda", "dssadsad"
        } );
        properties.put( "key11", new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9} );
        properties.put( "key12", new short[]{1, 2, 3, 4, 5, 6, 7, 8, 9} );
        properties.put( "key13", new long[]{1, 2, 3, 4, 5, 6, 7, 8, 9} );
        properties.put( "key14", new float[]{1, 2, 3, 4, 5, 6, 7, 8, 9} );
        properties.put( "key15", new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9} );
        properties.put( "key16", new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9} );
        properties.put( "key17", new boolean[]{true, false, true, false} );
        properties.put( "key18", new char[]{1, 2, 3, 4, 5, 6, 7, 8, 9} );
    }
    @Inject
    private TestDirectory testDirectory;
    @Inject
    private FileSystemAbstraction fs;
    @Inject
    private PageCache pageCache;
    @Inject
    private RecordDatabaseLayout databaseLayout;

    private DatabaseManagementService managementService;

    private static Stream<Arguments> params()
    {
        return Stream.of(
            arguments( 5 ),
            arguments( GraphDatabaseSettings.dense_node_threshold.defaultValue() )
        );
    }
    private enum RelTypes implements RelationshipType
    {
        BATCH_TEST,
        REL_TYPE1,
        REL_TYPE2,
        REL_TYPE3,
        REL_TYPE4,
        REL_TYPE5
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldUpdateStringArrayPropertiesOnNodesUsingBatchInserter1( int denseNodeThreshold ) throws Exception
    {
        // Given
        var inserter = newBatchInserter( denseNodeThreshold );

        String[] array1 = { "1" };
        String[] array2 = { "a" };

        long id1 = inserter.createNode(map("array", array1));
        long id2 = inserter.createNode( map() );

        // When
        inserter.getNodeProperties( id1 ).get( "array" );
        inserter.setNodeProperty( id1, "array", array1 );
        inserter.setNodeProperty( id2, "array", array2 );

        inserter.getNodeProperties( id1 ).get( "array" );
        inserter.setNodeProperty( id1, "array", array1 );
        inserter.setNodeProperty( id2, "array", array2 );

        // Then
        assertThat( inserter.getNodeProperties( id1 ).get( "array" ) ).isEqualTo( array1 );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void testSimple( int denseNodeThreshold ) throws Exception
    {
        var inserter = newBatchInserter( denseNodeThreshold );
        long node1 = inserter.createNode( null );
        long node2 = inserter.createNode( null );
        long rel1 = inserter.createRelationship( node1, node2, RelTypes.BATCH_TEST,
                null );
        BatchRelationship rel = inserter.getRelationshipById( rel1 );
        assertEquals( rel.getStartNode(), node1 );
        assertEquals( rel.getEndNode(), node2 );
        assertEquals( RelTypes.BATCH_TEST.name(), rel.getType().name() );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void testSetAndAddNodeProperties( int denseNodeThreshold ) throws Exception
    {
        var inserter = newBatchInserter( denseNodeThreshold );
        long tehNode = inserter.createNode( map( "one", "one", "two", "two", "three", "three" ) );
        inserter.setNodeProperty( tehNode, "four", "four" );
        inserter.setNodeProperty( tehNode, "five", "five" );
        Map<String, Object> props = getNodeProperties( inserter, tehNode );
        assertEquals( 5, props.size() );
        assertEquals( "one", props.get( "one" ) );
        assertEquals( "five", props.get( "five" ) );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void setSingleProperty( int denseNodeThreshold ) throws Exception
    {
        BatchInserter inserter = newBatchInserter( denseNodeThreshold );
        long nodeById = inserter.createNode( null );

        String value = "Something";
        String key = "name";
        inserter.setNodeProperty( nodeById, key, value );

        GraphDatabaseService db = switchToEmbeddedGraphDatabaseService( inserter, denseNodeThreshold );
        try ( var tx = db.beginTx() )
        {
            var node = tx.getNodeById( nodeById );
            assertThat( node.getProperty( key ) ).isEqualTo( value );
        }
        managementService.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void testSetAndKeepNodeProperty( int denseNodeThreshold ) throws Exception
    {
        var inserter = newBatchInserter( denseNodeThreshold );
        long tehNode = inserter.createNode( map( "foo", "bar" ) );
        inserter.setNodeProperty( tehNode, "foo2", "bar2" );
        Map<String, Object> props = getNodeProperties( inserter, tehNode );
        assertEquals( 2, props.size() );
        assertEquals( "bar", props.get( "foo" ) );
        assertEquals( "bar2", props.get( "foo2" ) );

        inserter.shutdown();

        inserter = newBatchInserter( denseNodeThreshold );

        props = getNodeProperties( inserter, tehNode );
        assertEquals( 2, props.size() );
        assertEquals( "bar", props.get( "foo" ) );
        assertEquals( "bar2", props.get( "foo2" ) );

        inserter.setNodeProperty( tehNode, "foo", "bar3" );

        props = getNodeProperties( inserter, tehNode );
        assertEquals( "bar3", props.get( "foo" ) );
        assertEquals( 2, props.size() );
        assertEquals( "bar3", props.get( "foo" ) );
        assertEquals( "bar2", props.get( "foo2" ) );

        inserter.shutdown();
        inserter = newBatchInserter( denseNodeThreshold );

        props = getNodeProperties( inserter, tehNode );
        assertEquals( "bar3", props.get( "foo" ) );
        assertEquals( 2, props.size() );
        assertEquals( "bar3", props.get( "foo" ) );
        assertEquals( "bar2", props.get( "foo2" ) );

        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void testSetAndKeepRelationshipProperty( int denseNodeThreshold ) throws Exception
    {
        var inserter = newBatchInserter( denseNodeThreshold );
        long from = inserter.createNode( Collections.emptyMap() );
        long to = inserter.createNode( Collections.emptyMap() );
        long theRel = inserter.createRelationship( from, to,
                RelationshipType.withName( "TestingPropsHere" ),
            map( "foo", "bar" ) );
        inserter.setRelationshipProperty( theRel, "foo2", "bar2" );
        Map<String, Object> props = getRelationshipProperties( inserter, theRel );
        assertEquals( 2, props.size() );
        assertEquals( "bar", props.get( "foo" ) );
        assertEquals( "bar2", props.get( "foo2" ) );

        inserter.shutdown();

        inserter = newBatchInserter( denseNodeThreshold );

        props = getRelationshipProperties( inserter, theRel );
        assertEquals( 2, props.size() );
        assertEquals( "bar", props.get( "foo" ) );
        assertEquals( "bar2", props.get( "foo2" ) );

        inserter.setRelationshipProperty( theRel, "foo", "bar3" );

        props = getRelationshipProperties( inserter, theRel );
        assertEquals( "bar3", props.get( "foo" ) );
        assertEquals( 2, props.size() );
        assertEquals( "bar3", props.get( "foo" ) );
        assertEquals( "bar2", props.get( "foo2" ) );

        inserter.shutdown();
        inserter = newBatchInserter( denseNodeThreshold );

        props = getRelationshipProperties( inserter, theRel );
        assertEquals( "bar3", props.get( "foo" ) );
        assertEquals( 2, props.size() );
        assertEquals( "bar3", props.get( "foo" ) );
        assertEquals( "bar2", props.get( "foo2" ) );

        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void testNodeHasProperty( int denseNodeThreshold ) throws Exception
    {
        var inserter = newBatchInserter( denseNodeThreshold );
        long theNode = inserter.createNode( properties );
        long anotherNode = inserter.createNode( Collections.emptyMap() );
        long relationship = inserter.createRelationship( theNode, anotherNode,
                RelationshipType.withName( "foo" ), properties );
        for ( String key : properties.keySet() )
        {
            assertTrue( inserter.nodeHasProperty( theNode, key ) );
            assertFalse( inserter.nodeHasProperty( theNode, key + "-" ) );
            assertTrue( inserter.relationshipHasProperty( relationship, key ) );
            assertFalse( inserter.relationshipHasProperty( relationship, key + "-" ) );
        }
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void testRemoveProperties( int denseNodeThreshold ) throws Exception
    {
        var inserter = newBatchInserter( denseNodeThreshold );
        long theNode = inserter.createNode( properties );
        long anotherNode = inserter.createNode( Collections.emptyMap() );
        long relationship = inserter.createRelationship( theNode, anotherNode,
                RelationshipType.withName( "foo" ), properties );

        inserter.removeNodeProperty( theNode, "key0" );
        inserter.removeRelationshipProperty( relationship, "key1" );

        for ( String key : properties.keySet() )
        {
            switch ( key )
            {
                case "key0":
                    assertFalse( inserter.nodeHasProperty( theNode, key ) );
                    assertTrue( inserter.relationshipHasProperty( relationship, key ) );
                    break;
                case "key1":
                    assertTrue( inserter.nodeHasProperty( theNode, key ) );
                    assertFalse( inserter.relationshipHasProperty( relationship,
                            key ) );
                    break;
                default:
                    assertTrue( inserter.nodeHasProperty( theNode, key ) );
                    assertTrue( inserter.relationshipHasProperty( relationship, key ) );
                    break;
            }
        }
        inserter.shutdown();
        inserter = newBatchInserter( denseNodeThreshold );

        for ( String key : properties.keySet() )
        {
            switch ( key )
            {
                case "key0":
                    assertFalse( inserter.nodeHasProperty( theNode, key ) );
                    assertTrue( inserter.relationshipHasProperty( relationship, key ) );
                    break;
                case "key1":
                    assertTrue( inserter.nodeHasProperty( theNode, key ) );
                    assertFalse( inserter.relationshipHasProperty( relationship,
                            key ) );
                    break;
                default:
                    assertTrue( inserter.nodeHasProperty( theNode, key ) );
                    assertTrue( inserter.relationshipHasProperty( relationship, key ) );
                    break;
            }
        }
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldBeAbleToRemoveDynamicProperty( int denseNodeThreshold ) throws Exception
    {
        // Only triggered if assertions are enabled

        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );
        String key = "tags";
        long nodeId = inserter.createNode( map( key, new String[]{"one", "two", "three"} ) );

        // WHEN
        inserter.removeNodeProperty( nodeId, key );

        // THEN
        assertFalse( inserter.getNodeProperties( nodeId ).containsKey( key ) );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldBeAbleToOverwriteDynamicProperty( int denseNodeThreshold ) throws Exception
    {
        // Only triggered if assertions are enabled

        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );
        String key = "tags";
        long nodeId = inserter.createNode( map( key, new String[]{"one", "two", "three"} ) );

        // WHEN
        String[] secondValue = {"four", "five", "six"};
        inserter.setNodeProperty( nodeId, key, secondValue );

        // THEN
        assertArrayEquals( secondValue, (String[]) getNodeProperties( inserter, nodeId ).get( key ) );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void testMore( int denseNodeThreshold ) throws Exception
    {
        var inserter = newBatchInserter( denseNodeThreshold );
        long startNode = inserter.createNode( properties );
        long[] endNodes = new long[25];
        Set<Long> rels = new HashSet<>();
        for ( int i = 0; i < 25; i++ )
        {
            endNodes[i] = inserter.createNode( properties );
            rels.add( inserter.createRelationship( startNode, endNodes[i],
                relTypeArray[i % 5], properties ) );
        }
        for ( BatchRelationship rel : inserter.getRelationships( startNode ) )
        {
            assertTrue( rels.contains( rel.getId() ) );
            assertEquals( rel.getStartNode(), startNode );
        }
        inserter.setNodeProperties( startNode, properties );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void makeSureLoopsCanBeCreated( int denseNodeThreshold ) throws Exception
    {
        var inserter = newBatchInserter( denseNodeThreshold );
        long startNode = inserter.createNode( properties );
        long otherNode = inserter.createNode( properties );
        long selfRelationship = inserter.createRelationship( startNode, startNode,
                relTypeArray[0], properties );
        long relationship = inserter.createRelationship( startNode, otherNode,
                relTypeArray[0], properties );
        for ( BatchRelationship rel : inserter.getRelationships( startNode ) )
        {
            if ( rel.getId() == selfRelationship )
            {
                assertEquals( startNode, rel.getStartNode() );
                assertEquals( startNode, rel.getEndNode() );
            }
            else if ( rel.getId() == relationship )
            {
                assertEquals( startNode, rel.getStartNode() );
                assertEquals( otherNode, rel.getEndNode() );
            }
            else
            {
                fail( "Unexpected relationship " + rel.getId() );
            }
        }

        GraphDatabaseService db = switchToEmbeddedGraphDatabaseService( inserter, denseNodeThreshold );

        try ( Transaction transaction = db.beginTx() )
        {
            Node realStartNode = transaction.getNodeById( startNode );
            Relationship realSelfRelationship = transaction.getRelationshipById( selfRelationship );
            Relationship realRelationship = transaction.getRelationshipById( relationship );
            assertEquals( realSelfRelationship,
                    realStartNode.getSingleRelationship( RelTypes.REL_TYPE1, Direction.INCOMING ) );
            assertEquals( asSet( realSelfRelationship, realRelationship ),
                    Iterables.asSet( realStartNode.getRelationships( Direction.OUTGOING ) ) );
            assertEquals( asSet( realSelfRelationship, realRelationship ),
                    Iterables.asSet( realStartNode.getRelationships() ) );
        }
        finally
        {
            managementService.shutdown();
        }
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void createBatchNodeAndRelationshipsDeleteAllInEmbedded( int denseNodeThreshold ) throws Exception
    {
        /*
         *    ()--[REL_TYPE1]-->(node)--[BATCH_TEST]->()
         */

        var inserter = newBatchInserter( denseNodeThreshold );
        long nodeId = inserter.createNode( null );
        inserter.createRelationship( nodeId, inserter.createNode( null ),
                RelTypes.BATCH_TEST, null );
        inserter.createRelationship( inserter.createNode( null ), nodeId,
                RelTypes.REL_TYPE1, null );

        // Delete node and all its relationships
        GraphDatabaseService db = switchToEmbeddedGraphDatabaseService( inserter, denseNodeThreshold );

        try ( Transaction tx = db.beginTx() )
        {
            Node node = tx.getNodeById( nodeId );
            for ( Relationship relationship : node.getRelationships() )
            {
                relationship.delete();
            }
            node.delete();
            tx.commit();
        }

        managementService.shutdown();
    }

    @Test
    void messagesLogGetsClosed() throws IOException
    {
        Config config = Config.newBuilder()
                .set( preallocate_logical_logs, false )
                .set( neo4j_home, testDirectory.homePath() )
                .build();
        BatchInserter inserter = BatchInserters.inserter( databaseLayout, fs, config );
        inserter.shutdown();
        Files.delete( databaseLayout.getNeo4jLayout().homeDirectory().resolve( INTERNAL_LOG_FILE ) );
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void createEntitiesWithEmptyPropertiesMap( int denseNodeThreshold ) throws Exception
    {
        var inserter = newBatchInserter( denseNodeThreshold );

        // Assert for node
        long nodeId = inserter.createNode( map() );
        getNodeProperties( inserter, nodeId );
        //cp=N U http://www.w3.org/1999/02/22-rdf-syntax-ns#type, c=N

        // Assert for relationship
        long anotherNodeId = inserter.createNode( null );
        long relId = inserter.createRelationship( nodeId, anotherNodeId, RelTypes.BATCH_TEST, map() );
        inserter.getRelationshipProperties( relId );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void createEntitiesWithDynamicPropertiesMap( int denseNodeThreshold ) throws Exception
    {
        var inserter = newBatchInserter( denseNodeThreshold );

        setAndGet( inserter, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" );
        setAndGet( inserter, intArray() );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldAddInitialLabelsToCreatedNode( int denseNodeThreshold ) throws Exception
    {
        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );

        // WHEN
        long node = inserter.createNode( map(), Labels.FIRST, Labels.SECOND );

        // THEN
        assertTrue( inserter.nodeHasLabel( node, Labels.FIRST ) );
        assertTrue( inserter.nodeHasLabel( node, Labels.SECOND ) );
        assertFalse( inserter.nodeHasLabel( node, Labels.THIRD ) );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldGetNodeLabels( int denseNodeThreshold ) throws Exception
    {
        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );
        long node = inserter.createNode( map(), Labels.FIRST, Labels.THIRD );

        // WHEN
        Iterable<String> labelNames = asNames( inserter.getNodeLabels( node ) );

        // THEN
        assertEquals( asSet( Labels.FIRST.name(), Labels.THIRD.name() ), Iterables.asSet( labelNames ) );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldAddManyInitialLabelsAsDynamicRecords( int denseNodeThreshold ) throws Exception
    {
        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );
        Pair<Label[], Set<String>> labels = manyLabels( 200 );
        long node = inserter.createNode( map(), labels.first() );
        forceFlush( inserter );

        // WHEN
        Iterable<String> labelNames = asNames( inserter.getNodeLabels( node ) );

        // THEN
        assertEquals( labels.other(), Iterables.asSet( labelNames ) );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldReplaceExistingInlinedLabelsWithDynamic( int denseNodeThreshold ) throws Exception
    {
        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );
        long node = inserter.createNode( map(), Labels.FIRST );

        // WHEN
        Pair<Label[], Set<String>> labels = manyLabels( 100 );
        inserter.setNodeLabels( node, labels.first() );

        // THEN
        Iterable<String> labelNames = asNames( inserter.getNodeLabels( node ) );
        assertEquals( labels.other(), Iterables.asSet( labelNames ) );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldReplaceExistingDynamicLabelsWithInlined( int denseNodeThreshold ) throws Exception
    {
        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );
        long node = inserter.createNode( map(), manyLabels( 150 ).first() );

        // WHEN
        inserter.setNodeLabels( node, Labels.FIRST );

        // THEN
        Iterable<String> labelNames = asNames( inserter.getNodeLabels( node ) );
        assertEquals( asSet( Labels.FIRST.name() ), Iterables.asSet( labelNames ) );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldRepopulatePreexistingIndexes( int denseNodeThreshold ) throws Throwable
    {
        // GIVEN
        var db = instantiateGraphDatabaseService( denseNodeThreshold );

        SchemaDescriptor schema;
        try ( var tx = db.beginTx() )
        {
            tx.schema().indexFor( label( "Hacker" ) ).on( "handle" ).create();
            var labelId = ((TransactionImpl) tx).kernelTransaction().tokenRead().nodeLabel( "Hacker" );
            var propId = ((TransactionImpl) tx).kernelTransaction().tokenRead().propertyKey( "handle" );
            schema = SchemaDescriptors.forLabel( labelId, propId );
            tx.commit();
        }

        long nodeId;
        try ( var tx = db.beginTx() )
        {
            tx.schema().awaitIndexesOnline( 1, TimeUnit.MINUTES );
            var node = tx.createNode( label( "Hacker" ) );
            node.setProperty( "handle", "Jakewins" );
            nodeId = node.getId();
            tx.commit();
        }

        managementService.shutdown();

        BatchInserter inserter = newBatchInserter( denseNodeThreshold );

        long boggle = inserter.createNode( map( "handle", "b0ggl3" ), label( "Hacker" ) );

        db = switchToEmbeddedGraphDatabaseService( inserter, denseNodeThreshold );
        try ( var tx = db.beginTx() )
        {
            assertThat( tx.findNodes( label( "Hacker" ) ).stream() ).hasSize( 2 );
            assertThat( tx.findNodes( label( "Hacker" ), "handle", "b0ggl3" ).stream().map( Entity::getId ) ).containsExactly( boggle );
        }
        managementService.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void propertiesCanBeReSetUsingBatchInserter( int denseNodeThreshold ) throws Exception
    {
        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );
        Map<String, Object> props = new HashMap<>();
        props.put( "name", "One" );
        props.put( "count", 1 );
        props.put( "tags", new String[] { "one", "two" } );
        props.put( "something", "something" );
        long nodeId = inserter.createNode( props );
        inserter.setNodeProperty( nodeId, "name", "NewOne" );
        inserter.removeNodeProperty( nodeId, "count" );
        inserter.removeNodeProperty( nodeId, "something" );

        // WHEN setting new properties
        inserter.setNodeProperty( nodeId, "name", "YetAnotherOne" );
        inserter.setNodeProperty( nodeId, "additional", "something" );

        // THEN there should be no problems doing so
        assertEquals( "YetAnotherOne", inserter.getNodeProperties( nodeId ).get( "name" ) );
        assertEquals("something", inserter.getNodeProperties( nodeId ).get( "additional" ) );
        inserter.shutdown();
    }

    /**
     * Test checks that during node property set we will cleanup not used property records
     * During initial node creation properties will occupy 5 property records.
     * Last property record will have only empty array for email.
     * During first update email property will be migrated to dynamic property and last property record will become
     * empty. That record should be deleted form property chain or otherwise on next node load user will get an
     * property record not in use exception.
     * @param denseNodeThreshold relationship group threshold from "params".
     */
    @ParameterizedTest
    @MethodSource( "params" )
    void testCleanupEmptyPropertyRecords( int denseNodeThreshold ) throws Exception
    {
        var inserter = newBatchInserter( denseNodeThreshold );

        Map<String, Object> properties = new HashMap<>();
        properties.put("id", 1099511659993L);
        properties.put("firstName", "Edward");
        properties.put("lastName", "Shevchenko");
        properties.put("gender", "male");
        properties.put( "birthday", new SimpleDateFormat( "yyyy-MM-dd" ).parse( "1987-11-08" ).getTime() );
        properties.put("birthday_month", 11);
        properties.put("birthday_day", 8);
        long time = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss.SSSZ" )
                        .parse( "2010-04-22T18:05:40.912+0000" )
                        .getTime();
        properties.put("creationDate", time );
        properties.put("locationIP", "46.151.255.205");
        properties.put( "browserUsed", "Firefox" );
        properties.put( "email", new String[0] );
        properties.put( "languages", new String[0] );
        long personNodeId = inserter.createNode(properties);

        assertEquals( "Shevchenko", getNodeProperties( inserter, personNodeId ).get( "lastName" ) );
        assertThat( (String[]) getNodeProperties( inserter, personNodeId ).get( "email" ) ).isEmpty();

        inserter.setNodeProperty( personNodeId, "email", new String[]{"Edward1099511659993@gmail.com"} );
        assertThat( (String[]) getNodeProperties( inserter, personNodeId ).get( "email" ) ).contains( "Edward1099511659993@gmail.com" );

        inserter.setNodeProperty( personNodeId, "email",
                new String[]{"Edward1099511659993@gmail.com", "backup@gmail.com"} );

        assertThat( (String[]) getNodeProperties( inserter, personNodeId ).get( "email" ) ).contains( "Edward1099511659993@gmail.com", "backup@gmail.com" );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void propertiesCanBeReSetUsingBatchInserter2( int denseNodeThreshold ) throws Exception
    {
        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );
        long id = inserter.createNode( new HashMap<>() );

        // WHEN
        inserter.setNodeProperty( id, "test", "looooooooooong test" );
        inserter.setNodeProperty( id, "test", "small test" );

        // THEN
        assertEquals( "small test", inserter.getNodeProperties( id ).get( "test" ) );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void replaceWithBiggerPropertySpillsOverIntoNewPropertyRecord( int denseNodeThreshold ) throws Exception
    {
        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );
        Map<String, Object> props = new HashMap<>();
        props.put( "name", "One" );
        props.put( "count", 1 );
        props.put( "tags", new String[] { "one", "two" } );
        long id = inserter.createNode( props );
        inserter.setNodeProperty( id, "name", "NewOne" );

        // WHEN
        inserter.setNodeProperty( id, "count", "something" );

        // THEN
        assertEquals( "something", inserter.getNodeProperties( id ).get( "count" ) );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void mustSplitUpRelationshipChainsWhenCreatingDenseNodes( int denseNodeThreshold ) throws Exception
    {
        var inserter = newBatchInserter( denseNodeThreshold );

        long node1 = inserter.createNode( null );
        long node2 = inserter.createNode( null );

        for ( int i = 0; i < 1000; i++ )
        {
            for ( MyRelTypes relType : MyRelTypes.values() )
            {
                inserter.createRelationship( node1, node2, relType, null );
            }
        }

        NeoStores neoStores = getFlushedNeoStores( inserter );
        NodeStore nodeStore = neoStores.getNodeStore();
        NodeRecord record = nodeStore.newRecord();
        try ( var cursor = nodeStore.openPageCursorForReading( 0, NULL ) )
        {
            nodeStore.getRecordByCursor( node1, record, NORMAL, cursor );
        }
        assertTrue( record.isDense(), "Node " + record + " should have been dense" );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldGetRelationships( int denseNodeThreshold ) throws Exception
    {
        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );
        long node = inserter.createNode( null );
        createRelationships( inserter, node, RelTypes.REL_TYPE1, 3 );
        createRelationships( inserter, node, RelTypes.REL_TYPE2, 4 );

        // WHEN
        Set<Long> gottenRelationships = Iterables.asSet( inserter.getRelationshipIds( node ) );

        // THEN
        assertEquals( 21, gottenRelationships.size() );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldNotCreateSameLabelTwiceOnSameNode( int denseNodeThreshold ) throws Exception
    {
        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );
        // WHEN
        long nodeId = inserter.createNode( map( "itemId", 1000L ), label( "Item" ),
                label( "Item" ) );

        // THEN
        NodeStore nodeStore = getFlushedNeoStores( inserter ).getNodeStore();
        NodeRecord node = nodeStore.newRecord();
        try ( var cursor = nodeStore.openPageCursorForReading( nodeId, NULL ) )
        {
            nodeStore.getRecordByCursor( nodeId, node, NORMAL, cursor );
        }
        NodeLabels labels = NodeLabelsField.parseLabelsField( node );
        long[] labelIds = labels.get( nodeStore, StoreCursors.NULL );
        assertEquals( 1, labelIds.length );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldSortLabelIdsWhenGetOrCreate( int denseNodeThreshold ) throws Exception
    {
        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );

        // WHEN
        long nodeId = inserter.createNode( map( "Item", 123456789123L ), label( "AA" ),
                label( "BB" ), label( "CC" ), label( "DD" ) );
        inserter.setNodeLabels( nodeId, label( "CC" ), label( "AA" ),
                label( "DD" ), label( "EE" ), label( "FF" ) );

        // THEN
        NodeStore nodeStore = getFlushedNeoStores( inserter ).getNodeStore();
        NodeRecord node = nodeStore.newRecord();
        try ( var cursor = nodeStore.openPageCursorForReading( nodeId, NULL ) )
        {
            nodeStore.getRecordByCursor( nodeId, node, NORMAL, cursor );
        }
        NodeLabels labels = NodeLabelsField.parseLabelsField( node );

        long[] labelIds = labels.get( nodeStore, StoreCursors.NULL );
        long[] sortedLabelIds = Arrays.copyOf( labelIds, labelIds.length );
        Arrays.sort( sortedLabelIds );
        assertArrayEquals( sortedLabelIds, labelIds );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldChangePropertiesInCurrentBatch( int denseNodeThreshold ) throws Exception
    {
        // GIVEN
        var inserter = newBatchInserter( denseNodeThreshold );
        Map<String,Object> properties = map( "key1", "value1" );
        long node = inserter.createNode( properties );

        // WHEN
        properties.put( "additionalKey", "Additional value" );
        inserter.setNodeProperties( node, properties );

        // THEN
        assertEquals( properties, getNodeProperties( inserter, node ) );
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldIgnoreRemovingNonExistentNodeProperty( int denseNodeThreshold ) throws Exception
    {
        // given
        var inserter = newBatchInserter( denseNodeThreshold );
        long id = inserter.createNode( Collections.emptyMap() );

        // when
        inserter.removeNodeProperty( id, "non-existent" );

        // then no exception should be thrown, this mimics GraphDatabaseService behaviour
        inserter.shutdown();
    }

    @ParameterizedTest
    @MethodSource( "params" )
    void shouldIgnoreRemovingNonExistentRelationshipProperty( int denseNodeThreshold ) throws Exception
    {
        // given
        var inserter = newBatchInserter( denseNodeThreshold );
        Map<String,Object> noProperties = Collections.emptyMap();
        long nodeId1 = inserter.createNode( noProperties );
        long nodeId2 = inserter.createNode( noProperties );
        long id = inserter.createRelationship( nodeId1, nodeId2, MyRelTypes.TEST, noProperties );

        // when
        inserter.removeRelationshipProperty( id, "non-existent" );

        // then no exception should be thrown, this mimics GraphDatabaseService behaviour
        inserter.shutdown();
    }

    @Test
    void shouldStartOnAndUpdateDbContainingFulltextIndex() throws Exception
    {
        // given
        // this test cannot run on an impermanent db since there's a test issue causing problems when flipping/closing RAMDirectory Lucene indexes
        int denseNodeThreshold = GraphDatabaseSettings.dense_node_threshold.defaultValue();
        GraphDatabaseService db = instantiateGraphDatabaseService( denseNodeThreshold );
        String key = "key";
        Label label = Label.label( "Label" );
        String indexName = "ftsNodes";
        try
        {
            try ( Transaction tx = db.beginTx() )
            {
                db.executeTransactionally( format( "CREATE FULLTEXT INDEX %s FOR (n:%s) ON EACH [n.%s]", indexName, label.name(), key ) );
                tx.commit();
            }
            try ( Transaction tx = db.beginTx() )
            {
                tx.schema().awaitIndexesOnline( 2, TimeUnit.MINUTES );
                tx.commit();
            }
        }
        finally
        {
            managementService.shutdown();
        }

        // when
        String value = "hey";
        BatchInserter inserter = newBatchInserter( denseNodeThreshold );
        long node = inserter.createNode( Collections.singletonMap( key, value ), label );

        // then
        inserter.shutdown();
        GraphDatabaseAPI dbAfterInsert = instantiateGraphDatabaseService( denseNodeThreshold );
        try
        {
            try ( Transaction tx = dbAfterInsert.beginTx() )
            {
                // Check that the store has this node
                ResourceIterator<Node> nodes = tx.findNodes( label, key, value );
                Node foundNode = Iterators.single( nodes );
                assertEquals( node, foundNode.getId() );

                // Check that the fulltext index has this node
                dbAfterInsert.executeTransactionally( format( "CALL db.index.fulltext.queryNodes('%s', '%s')", indexName, value ), new HashMap<>(), result ->
                {
                    assertTrue( result.hasNext() );
                    Map<String,Object> hit = result.next();
                    Node indexedNode = (Node) hit.get( "node" );
                    assertFalse( result.hasNext() );
                    return indexedNode;
                } );
            }
        }
        finally
        {
            managementService.shutdown();
        }
    }

    @Test
    void shouldBuildCorrectCountsStoreOnIncrementalImport() throws Exception
    {
        // given
        Label label = Label.label( "Person" );
        int denseNodeThreshold = dense_node_threshold.defaultValue();
        for ( int r = 0; r < 3; r++ )
        {
            // when
            BatchInserter inserter = newBatchInserter( denseNodeThreshold );
            try
            {
                for ( int i = 0; i < 100; i++ )
                {
                    inserter.createNode( null, label );
                }
            }
            finally
            {
                inserter.shutdown();
            }

            // then
            try ( GBPTreeCountsStore countsStore = new GBPTreeCountsStore( pageCache, databaseLayout.countStore(), fs, RecoveryCleanupWorkCollector.immediate(),
                    new CountsBuilder()
                    {
                        @Override
                        public void initialize( CountsAccessor.Updater updater, CursorContext cursorContext, MemoryTracker memoryTracker )
                        {
                            throw new UnsupportedOperationException( "Should not be required" );
                        }

                        @Override
                        public long lastCommittedTxId()
                        {
                            return TransactionIdStore.BASE_TX_ID;
                        }
                    }, readOnly(), PageCacheTracer.NULL, GBPTreeCountsStore.NO_MONITOR, databaseLayout.getDatabaseName(), 1000,
                    NullLogProvider.getInstance() ) )
            {
                countsStore.start( NULL, StoreCursors.NULL, INSTANCE );
                assertEquals( (r + 1) * 100, countsStore.nodeCount( 0, NULL ) );
            }
        }
    }

    @Test
    void shouldIncrementDegreesOnUpdatingDenseNode() throws Exception
    {
        // given
        int denseNodeThreshold = 10;
        BatchInserter inserter = newBatchInserter( configurationBuilder()
                .set( dense_node_threshold, 10 )
                // Make it flush (and empty the record changes set) multiple times during this insertion
                .set( GraphDatabaseInternalSettings.batch_inserter_batch_size, 2 )
                .build() );
        long denseNode = inserter.createNode( null );

        // when
        for ( int i = 0; i < denseNodeThreshold * 2; i++ )
        {
            inserter.createRelationship( denseNode, inserter.createNode( null ), relTypeArray[0], null );
        }

        // then
        GraphDatabaseAPI db = switchToEmbeddedGraphDatabaseService( inserter, denseNodeThreshold );
        try ( Transaction tx = db.beginTx() )
        {
            assertThat( tx.getNodeById( denseNode ).getDegree( relTypeArray[0], Direction.OUTGOING ) ).isEqualTo( denseNodeThreshold * 2 );
        }
        managementService.shutdown();
    }

    private Config.Builder configurationBuilder()
    {
        return Config.newBuilder()
                .set( neo4j_home, testDirectory.absolutePath() )
                .set( preallocate_logical_logs, false );
    }

    private Config configuration( int denseNodeThreshold )
    {
        return configurationBuilder()
                .set( GraphDatabaseSettings.dense_node_threshold, denseNodeThreshold )
                .build();
    }

    private BatchInserter newBatchInserter( int denseNodeThreshold ) throws Exception
    {
        return newBatchInserter( configuration( denseNodeThreshold ) );
    }

    private BatchInserter newBatchInserter( Config config ) throws Exception
    {
        return BatchInserters.inserter( databaseLayout, fs, config );
    }

    private BatchInserter newBatchInserterWithIndexProvider( ExtensionFactory<?> provider, IndexProviderDescriptor providerDescriptor, int denseNodeThreshold )
        throws Exception
    {
        Config configuration = configuration( denseNodeThreshold );
        configuration.set( GraphDatabaseSettings.default_schema_provider, providerDescriptor.name() );
        return BatchInserters.inserter(
                databaseLayout, fs, configuration );
    }

    private GraphDatabaseAPI switchToEmbeddedGraphDatabaseService( BatchInserter inserter, int denseNodeThreshold )
    {
        inserter.shutdown();
        return instantiateGraphDatabaseService( denseNodeThreshold );
    }

    private GraphDatabaseAPI instantiateGraphDatabaseService( int denseNodeThreshold )
    {
        TestDatabaseManagementServiceBuilder factory = new TestDatabaseManagementServiceBuilder( databaseLayout );
        factory.setFileSystem( fs );
        managementService = factory.impermanent()
                                   // Shouldn't be necessary to set dense node threshold since it's a stick config
                                   .setConfig( configuration( denseNodeThreshold ) ).build();
        return (GraphDatabaseAPI) managementService.database( DEFAULT_DATABASE_NAME );
    }

    private static void createRelationships( BatchInserter inserter, long node, RelationshipType relType, int out )
    {
        for ( int i = 0; i < out; i++ )
        {
            inserter.createRelationship( node, inserter.createNode( null ), relType, null );
        }
        for ( int i = 0; i < out; i++ )
        {
            inserter.createRelationship( inserter.createNode( null ), node, relType, null );
        }
        for ( int i = 0; i < out; i++ )
        {
            inserter.createRelationship( node, node, relType, null );
        }
    }

    private static void setAndGet( BatchInserter inserter, Object value )
    {
        long nodeId = inserter.createNode( map( "key", value ) );
        Object readValue = inserter.getNodeProperties( nodeId ).get( "key" );
        if ( readValue.getClass().isArray() )
        {
            assertArrayEquals( (int[]) value, (int[]) readValue );
        }
        else
        {
            assertEquals( value, readValue );
        }
    }

    private static int[] intArray()
    {
        int length = 20;
        int[] array = new int[length];
        for ( int i = 0, startValue = 1 << 30; i < length; i++ )
        {
            array[i] = startValue + i;
        }
        return array;
    }

    private static void forceFlush( BatchInserter inserter )
    {
        ((BatchInserterImpl)inserter).forceFlushChanges();
    }

    private static NeoStores getFlushedNeoStores( BatchInserter inserter )
    {
        forceFlush( inserter );
        return ((BatchInserterImpl) inserter).getNeoStores();
    }

    private enum Labels implements Label
    {
        FIRST,
        SECOND,
        THIRD
    }

    private static Iterable<String> asNames( Iterable<Label> nodeLabels )
    {
        return map( Label::name, nodeLabels );
    }

    private static Pair<Label[], Set<String>> manyLabels( int count )
    {
        Label[] labels = new Label[count];
        Set<String> expectedLabelNames = new HashSet<>();
        for ( int i = 0; i < labels.length; i++ )
        {
            String labelName = "bach label " + i;
            labels[i] = label( labelName );
            expectedLabelNames.add( labelName );
        }
        return Pair.of( labels, expectedLabelNames );
    }

    private static Map<String, Object> getNodeProperties( BatchInserter inserter, long nodeId )
    {
        return inserter.getNodeProperties( nodeId );
    }

    private static Map<String, Object> getRelationshipProperties( BatchInserter inserter, long relId )
    {
        return inserter.getRelationshipProperties( relId );
    }
}
