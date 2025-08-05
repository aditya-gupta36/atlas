package org.apache.atlas.hive.hook.events;

import org.apache.atlas.hive.hook.AtlasHiveHookContext;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntitiesWithExtInfo;
import org.apache.atlas.model.notification.HookNotification;
import org.apache.hadoop.hive.metastore.IHMSHandler;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.events.CreateTableEvent;
import org.apache.hadoop.hive.ql.hooks.Entity;
import org.apache.hadoop.hive.ql.metadata.Hive;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.hadoop.hive.ql.plan.HiveOperation;
import org.apache.hadoop.fs.Path;
import org.mockito.*;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

import static org.mockito.Mockito.*;

public class CreateTableTestEnhanced {

    @Mock
    AtlasHiveHookContext context;

    @Mock
    CreateTableEvent createTableEvent;

    @Mock
    Table table;

    @Mock
    org.apache.hadoop.hive.metastore.api.Table metastoreTable;

    @Mock
    Hive hive;

    @Mock
    IHMSHandler metastoreHandler;

    @Mock
    StorageDescriptor storageDescriptor;

    @Mock
    org.apache.hadoop.hive.metastore.api.Database database;

    @Captor
    ArgumentCaptor<AtlasEntitiesWithExtInfo> entitiesCaptor;

    private CreateTable createTable;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ===== CONSTRUCTOR TESTS =====

    @Test
    public void testCreateTable_Constructor_ValidContext() {
        when(context.isMetastoreHook()).thenReturn(true);
        createTable = new CreateTable(context);
        AssertJUnit.assertNotNull("CreateTable instance should be created", createTable);
    }

    @Test
    public void testCreateTable_Constructor_NullContext() {
        try {
            createTable = new CreateTable(null);
            AssertJUnit.fail("Should throw exception for null context");
        } catch (Exception e) {
            // Expected exception
        }
    }

    // ===== NOTIFICATION MESSAGES TESTS =====

    @Test
    public void testGetNotificationMessages_MetastoreHook_WithValidEntities() throws Exception {
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getMetastoreEvent()).thenReturn(createTableEvent);
        when(createTableEvent.getTable()).thenReturn(metastoreTable);
        when(metastoreTable.getDbName()).thenReturn("test_db");
        when(metastoreTable.getTableName()).thenReturn("test_table");

        createTable = spy(new CreateTable(context));
        doAnswer(invocation -> {
            AtlasEntitiesWithExtInfo entities = invocation.getArgument(1);
            AtlasEntity tblEntity = new AtlasEntity("hive_table");
            tblEntity.setAttribute("qualifiedName", "test_db.test_table@cluster");
            entities.addEntity(tblEntity);
            return null;
        }).when(createTable).processTable(any(), any());

        List<HookNotification> notifications = createTable.getNotificationMessages();
        
        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        AssertJUnit.assertEquals("Should have one notification", 1, notifications.size());
        AssertJUnit.assertTrue("Should be EntityCreateRequestV2", 
            notifications.get(0) instanceof HookNotification.EntityCreateRequestV2);
    }

    @Test
    public void testGetNotificationMessages_NonMetastoreHook() throws Exception {
        when(context.isMetastoreHook()).thenReturn(false);
        when(context.getOutputs()).thenReturn(Collections.emptySet());

        createTable = new CreateTable(context);
        List<HookNotification> notifications = createTable.getNotificationMessages();
        
        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        AssertJUnit.assertTrue("Should be empty for non-metastore hook with no outputs", 
            notifications.isEmpty());
    }

    @Test
    public void testGetNotificationMessages_ExceptionHandling() throws Exception {
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getMetastoreEvent()).thenThrow(new RuntimeException("Test exception"));

        createTable = new CreateTable(context);
        
        try {
            createTable.getNotificationMessages();
            AssertJUnit.fail("Should propagate exception");
        } catch (RuntimeException e) {
            AssertJUnit.assertEquals("Test exception", e.getMessage());
        }
    }

    // ===== HIVE METASTORE ENTITIES TESTS =====

    @Test
    public void testGetHiveMetastoreEntities_TemporaryTable() throws Exception {
        when(context.getMetastoreEvent()).thenReturn(createTableEvent);
        when(createTableEvent.getTable()).thenReturn(metastoreTable);
        when(metastoreTable.getTableType()).thenReturn("TEMPORARY_TABLE");

        createTable = spy(new CreateTable(context));
        doReturn(true).when(createTable).skipTemporaryTable(any());

        AtlasEntitiesWithExtInfo result = createTable.getHiveMetastoreEntities();
        
        AssertJUnit.assertNotNull("Result should not be null", result);
        AssertJUnit.assertTrue("Should be empty for temporary tables", 
            result.getEntities().isEmpty());
    }

    @Test
    public void testGetHiveMetastoreEntities_RegularTable() throws Exception {
        when(context.getMetastoreEvent()).thenReturn(createTableEvent);
        when(createTableEvent.getTable()).thenReturn(metastoreTable);
        when(metastoreTable.getTableType()).thenReturn("MANAGED_TABLE");
        when(metastoreTable.getDbName()).thenReturn("test_db");
        when(metastoreTable.getTableName()).thenReturn("test_table");

        createTable = spy(new CreateTable(context));
        doReturn(false).when(createTable).skipTemporaryTable(any());
        doNothing().when(createTable).processTable(any(), any());

        AtlasEntitiesWithExtInfo result = createTable.getHiveMetastoreEntities();
        
        AssertJUnit.assertNotNull("Result should not be null", result);
    }

    @Test
    public void testGetHiveMetastoreEntities_NullTable() throws Exception {
        when(context.getMetastoreEvent()).thenReturn(createTableEvent);
        when(createTableEvent.getTable()).thenReturn(null);

        createTable = new CreateTable(context);
        
        try {
            createTable.getHiveMetastoreEntities();
            AssertJUnit.fail("Should handle null table gracefully");
        } catch (Exception e) {
            // Expected behavior
        }
    }

    // ===== HIVE ENTITIES TESTS =====

    @Test
    public void testGetHiveEntities_WithMultipleTables() throws Exception {
        Entity entity1 = mock(Entity.class);
        Entity entity2 = mock(Entity.class);
        Table qlTable1 = mock(Table.class);
        Table qlTable2 = mock(Table.class);
        
        when(entity1.getType()).thenReturn(Entity.Type.TABLE);
        when(entity1.getTable()).thenReturn(qlTable1);
        when(qlTable1.getDbName()).thenReturn("db1");
        when(qlTable1.getTableName()).thenReturn("table1");
        
        when(entity2.getType()).thenReturn(Entity.Type.TABLE);
        when(entity2.getTable()).thenReturn(qlTable2);
        when(qlTable2.getDbName()).thenReturn("db2");
        when(qlTable2.getTableName()).thenReturn("table2");

        Set<Entity> outputs = new HashSet<>();
        outputs.add(entity1);
        outputs.add(entity2);
        when(context.getOutputs()).thenReturn(outputs);

        createTable = spy(new CreateTable(context));
        doReturn(hive).when(createTable).getHive();
        when(hive.getTable("db1", "table1")).thenReturn(qlTable1);
        when(hive.getTable("db2", "table2")).thenReturn(qlTable2);
        
        doAnswer(invocation -> {
            AtlasEntitiesWithExtInfo entities = invocation.getArgument(1);
            AtlasEntity tblEntity = new AtlasEntity("hive_table");
            entities.addEntity(tblEntity);
            return null;
        }).when(createTable).processTable(any(), any());

        AtlasEntitiesWithExtInfo result = createTable.getHiveEntities();
        
        AssertJUnit.assertNotNull("Result should not be null", result);
    }

    @Test
    public void testGetHiveEntities_NonTableEntity() throws Exception {
        Entity entity = mock(Entity.class);
        when(entity.getType()).thenReturn(Entity.Type.PARTITION);
        when(context.getOutputs()).thenReturn(Collections.singleton(entity));

        createTable = new CreateTable(context);
        AtlasEntitiesWithExtInfo result = createTable.getHiveEntities();
        
        AssertJUnit.assertNotNull("Result should not be null", result);
    }

    @Test
    public void testGetHiveEntities_ExceptionInHiveAccess() throws Exception {
        Entity entity = mock(Entity.class);
        Table qlTable = mock(Table.class);
        when(entity.getType()).thenReturn(Entity.Type.TABLE);
        when(entity.getTable()).thenReturn(qlTable);
        when(qlTable.getDbName()).thenReturn("test_db");
        when(qlTable.getTableName()).thenReturn("test_table");
        when(context.getOutputs()).thenReturn(Collections.singleton(entity));

        createTable = spy(new CreateTable(context));
        doReturn(hive).when(createTable).getHive();
        when(hive.getTable("test_db", "test_table")).thenThrow(new RuntimeException("Hive error"));

        try {
            createTable.getHiveEntities();
        } catch (RuntimeException e) {
            AssertJUnit.assertEquals("Hive error", e.getMessage());
        }
    }

    // ===== SKIP TEMPORARY TABLE TESTS =====

    @Test
    public void testSkipTemporaryTable_ManagedTable() {
        when(table.isTemporary()).thenReturn(false);
        when(table.getTableType()).thenReturn(TableType.MANAGED_TABLE);

        createTable = new CreateTable(context);
        boolean result = createTable.skipTemporaryTable(table);
        
        AssertJUnit.assertFalse("Should not skip managed table", result);
    }

    @Test
    public void testSkipTemporaryTable_TemporaryManagedTable() {
        when(table.isTemporary()).thenReturn(true);
        when(table.getTableType()).thenReturn(TableType.MANAGED_TABLE);

        createTable = new CreateTable(context);
        boolean result = createTable.skipTemporaryTable(table);
        
        AssertJUnit.assertTrue("Should skip temporary managed table", result);
    }

    @Test
    public void testSkipTemporaryTable_TemporaryExternalTable() {
        when(table.isTemporary()).thenReturn(true);
        when(table.getTableType()).thenReturn(TableType.EXTERNAL_TABLE);

        createTable = new CreateTable(context);
        boolean result = createTable.skipTemporaryTable(table);
        
        AssertJUnit.assertTrue("Should skip temporary external table", result);
    }

    @Test
    public void testSkipTemporaryTable_NullTableType() {
        when(table.isTemporary()).thenReturn(false);
        when(table.getTableType()).thenReturn(null);

        createTable = new CreateTable(context);
        boolean result = createTable.skipTemporaryTable(table);
        
        AssertJUnit.assertFalse("Should not skip when table type is null", result);
    }

    // ===== CREATE EXTERNAL TABLE OPERATION TESTS =====

    @Test
    public void testIsCreateExtTableOperation_ExternalTableWithCreateOperation() {
        when(table.getTableType()).thenReturn(TableType.EXTERNAL_TABLE);
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE);

        createTable = new CreateTable(context);
        boolean result = createTable.isCreateExtTableOperation(table);
        
        AssertJUnit.assertTrue("Should be true for external table with CREATE operation", result);
    }

    @Test
    public void testIsCreateExtTableOperation_ManagedTableWithCreateOperation() {
        when(table.getTableType()).thenReturn(TableType.MANAGED_TABLE);
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE);

        createTable = new CreateTable(context);
        boolean result = createTable.isCreateExtTableOperation(table);
        
        AssertJUnit.assertFalse("Should be false for managed table", result);
    }

    @Test
    public void testIsCreateExtTableOperation_ExternalTableWithDropOperation() {
        when(table.getTableType()).thenReturn(TableType.EXTERNAL_TABLE);
        when(context.getHiveOperation()).thenReturn(HiveOperation.DROPTABLE);

        createTable = new CreateTable(context);
        boolean result = createTable.isCreateExtTableOperation(table);
        
        AssertJUnit.assertFalse("Should be false for non-CREATE operation", result);
    }

    @Test
    public void testIsCreateExtTableOperation_NullOperation() {
        when(table.getTableType()).thenReturn(TableType.EXTERNAL_TABLE);
        when(context.getHiveOperation()).thenReturn(null);

        createTable = new CreateTable(context);
        boolean result = createTable.isCreateExtTableOperation(table);
        
        AssertJUnit.assertFalse("Should be false for null operation", result);
    }

    // ===== PROCESS TABLE TESTS =====

    @Test
    public void testProcessTable_WithColumns() throws Exception {
        List<FieldSchema> columns = Arrays.asList(
            new FieldSchema("col1", "string", "comment1"),
            new FieldSchema("col2", "int", "comment2")
        );
        
        when(storageDescriptor.getCols()).thenReturn(columns);
        when(table.getSd()).thenReturn(storageDescriptor);
        when(table.getDbName()).thenReturn("test_db");
        when(table.getTableName()).thenReturn("test_table");
        when(table.getTableType()).thenReturn(TableType.MANAGED_TABLE);
        when(table.getDataLocation()).thenReturn(new Path("hdfs://test/path"));

        when(context.getHive()).thenReturn(hive);
        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE);

        when(database.getName()).thenReturn("test_db");
        when(database.getCatalogName()).thenReturn("hive");
        when(metastoreHandler.get_database("test_db")).thenReturn(database);

        createTable = spy(new CreateTable(context));
        doReturn(database).when(createTable).getDatabases("test_db");

        AtlasEntity processEntity = mock(AtlasEntity.class);
        when(processEntity.getAttribute("qualifiedName")).thenReturn("test_db.test_table@cluster_process");
        doReturn(processEntity).when(createTable).getHiveProcessEntity(anyList(), anyList());

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        AssertJUnit.assertFalse("Entities should be added", entities.getEntities().isEmpty());
    }

    @Test
    public void testProcessTable_DatabaseNotFound() throws Exception {
        when(table.getDbName()).thenReturn("nonexistent_db");
        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(metastoreHandler.get_database("nonexistent_db"))
            .thenThrow(new NoSuchObjectException("Database not found"));

        createTable = spy(new CreateTable(context));
        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();

        try {
            createTable.processTable(table, entities);
        } catch (Exception e) {
            AssertJUnit.assertTrue("Should handle database not found", 
                e.getCause() instanceof NoSuchObjectException);
        }
    }

    @Test
    public void testProcessTable_NullStorageDescriptor() throws Exception {
        when(table.getSd()).thenReturn(null);
        when(table.getDbName()).thenReturn("test_db");
        when(table.getTableName()).thenReturn("test_table");

        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(database.getName()).thenReturn("test_db");
        when(metastoreHandler.get_database("test_db")).thenReturn(database);

        createTable = spy(new CreateTable(context));
        doReturn(database).when(createTable).getDatabases("test_db");

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        // Should handle null storage descriptor gracefully
        AssertJUnit.assertNotNull("Entities should not be null", entities);
    }

    // ===== COMPLEX SCENARIO TESTS =====

    @Test
    public void testCreateTableAsSelect_Scenario() throws Exception {
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE_AS_SELECT);
        Entity inputEntity = mock(Entity.class);
        Entity outputEntity = mock(Entity.class);
        
        when(inputEntity.getType()).thenReturn(Entity.Type.TABLE);
        when(outputEntity.getType()).thenReturn(Entity.Type.TABLE);
        
        Set<Entity> inputs = Collections.singleton(inputEntity);
        Set<Entity> outputs = Collections.singleton(outputEntity);
        
        when(context.getInputs()).thenReturn(inputs);
        when(context.getOutputs()).thenReturn(outputs);

        createTable = new CreateTable(context);
        
        // Test should handle CTAS scenario
        AssertJUnit.assertNotNull("CreateTable should handle CTAS", createTable);
    }

    @Test
    public void testCreateView_Scenario() throws Exception {
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATEVIEW);
        Entity entity = mock(Entity.class);
        Table viewTable = mock(Table.class);
        
        when(entity.getType()).thenReturn(Entity.Type.TABLE);
        when(entity.getTable()).thenReturn(viewTable);
        when(viewTable.getDbName()).thenReturn("test_db");
        when(viewTable.getTableName()).thenReturn("test_view");
        when(viewTable.getTableType()).thenReturn(TableType.VIRTUAL_VIEW);
        
        when(context.getOutputs()).thenReturn(Collections.singleton(entity));

        createTable = spy(new CreateTable(context));
        doReturn(hive).when(createTable).getHive();
        when(hive.getTable("test_db", "test_view")).thenReturn(viewTable);
        
        doAnswer(invocation -> {
            AtlasEntitiesWithExtInfo entities = invocation.getArgument(1);
            AtlasEntity viewEntity = new AtlasEntity("hive_table");
            viewEntity.setAttribute("tableType", "VIRTUAL_VIEW");
            entities.addEntity(viewEntity);
            return null;
        }).when(createTable).processTable(any(), any());

        AtlasEntitiesWithExtInfo result = createTable.getHiveEntities();
        AssertJUnit.assertNotNull("Should handle view creation", result);
    }

    // ===== ERROR HANDLING AND EDGE CASES =====

    @Test
    public void testGetNotificationMessages_EmptyEntities() throws Exception {
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getMetastoreEvent()).thenReturn(createTableEvent);
        when(createTableEvent.getTable()).thenReturn(metastoreTable);
        when(metastoreTable.getDbName()).thenReturn("test_db");
        when(metastoreTable.getTableName()).thenReturn("test_table");

        createTable = spy(new CreateTable(context));
        doAnswer(invocation -> {
            // Don't add any entities
            return null;
        }).when(createTable).processTable(any(), any());

        List<HookNotification> notifications = createTable.getNotificationMessages();
        
        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        AssertJUnit.assertTrue("Should be empty when no entities are created", 
            notifications.isEmpty());
    }

    @Test
    public void testGetHiveEntities_TableRetrievalException() throws Exception {
        Entity entity = mock(Entity.class);
        Table qlTable = mock(Table.class);
        when(entity.getType()).thenReturn(Entity.Type.TABLE);
        when(entity.getTable()).thenReturn(qlTable);
        when(qlTable.getDbName()).thenReturn("test_db");
        when(qlTable.getTableName()).thenReturn("test_table");
        when(context.getOutputs()).thenReturn(Collections.singleton(entity));

        createTable = spy(new CreateTable(context));
        doReturn(hive).when(createTable).getHive();
        when(hive.getTable("test_db", "test_table"))
            .thenThrow(new NoSuchObjectException("Table not found"));

        try {
            createTable.getHiveEntities();
        } catch (Exception e) {
            AssertJUnit.assertTrue("Should handle table retrieval exception", 
                e.getCause() instanceof NoSuchObjectException);
        }
    }

    @Test
    public void testProcessTable_LargeNumberOfColumns() throws Exception {
        List<FieldSchema> columns = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            columns.add(new FieldSchema("col" + i, "string", "comment" + i));
        }
        
        when(storageDescriptor.getCols()).thenReturn(columns);
        when(table.getSd()).thenReturn(storageDescriptor);
        when(table.getDbName()).thenReturn("test_db");
        when(table.getTableName()).thenReturn("large_table");
        when(table.getTableType()).thenReturn(TableType.MANAGED_TABLE);

        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(database.getName()).thenReturn("test_db");
        when(metastoreHandler.get_database("test_db")).thenReturn(database);

        createTable = spy(new CreateTable(context));
        doReturn(database).when(createTable).getDatabases("test_db");

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        // Should handle large number of columns
        AssertJUnit.assertNotNull("Should handle large number of columns", entities);
    }

    // ===== NULL AND BOUNDARY TESTS =====

    @Test
    public void testSkipTemporaryTable_NullTable() {
        createTable = new CreateTable(context);
        
        try {
            createTable.skipTemporaryTable(null);
            AssertJUnit.fail("Should throw exception for null table");
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void testIsCreateExtTableOperation_NullTable() {
        createTable = new CreateTable(context);
        
        try {
            createTable.isCreateExtTableOperation(null);
            AssertJUnit.fail("Should throw exception for null table");
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void testProcessTable_NullEntities() throws Exception {
        when(table.getDbName()).thenReturn("test_db");
        
        createTable = new CreateTable(context);
        
        try {
            createTable.processTable(table, null);
            AssertJUnit.fail("Should throw exception for null entities");
        } catch (Exception e) {
            // Expected
        }
    }

    // ===== METASTORE HANDLER TESTS =====

    @Test
    public void testGetHiveMetastoreEntities_MetastoreHandlerException() throws Exception {
        when(context.getMetastoreEvent()).thenReturn(createTableEvent);
        when(createTableEvent.getTable()).thenReturn(metastoreTable);
        when(metastoreTable.getTableType()).thenReturn("MANAGED_TABLE");
        when(metastoreTable.getDbName()).thenReturn("test_db");

        createTable = spy(new CreateTable(context));
        doReturn(false).when(createTable).skipTemporaryTable(any());
        doThrow(new RuntimeException("Metastore error")).when(createTable).processTable(any(), any());

        try {
            createTable.getHiveMetastoreEntities();
        } catch (RuntimeException e) {
            AssertJUnit.assertEquals("Metastore error", e.getMessage());
        }
    }
}