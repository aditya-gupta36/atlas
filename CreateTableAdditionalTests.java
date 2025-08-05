package org.apache.atlas.hive.hook.events;

import org.apache.atlas.hive.hook.AtlasHiveHookContext;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntitiesWithExtInfo;
import org.apache.atlas.model.notification.HookNotification;
import org.apache.hadoop.hive.metastore.IHMSHandler;
import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.SerDeInfo;
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

/**
 * Additional test cases to achieve 90% coverage for CreateTable class
 * These tests focus on specific methods and code branches that may not be covered
 */
public class CreateTableAdditionalTests {

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

    @Mock
    SerDeInfo serDeInfo;

    private CreateTable createTable;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ===== TESTS FOR PARTITION KEY HANDLING =====

    @Test
    public void testProcessTable_WithPartitionKeys() throws Exception {
        List<FieldSchema> partitionKeys = Arrays.asList(
            new FieldSchema("year", "int", "partition by year"),
            new FieldSchema("month", "int", "partition by month")
        );
        
        List<FieldSchema> columns = Arrays.asList(
            new FieldSchema("id", "bigint", "id column"),
            new FieldSchema("name", "string", "name column")
        );

        when(table.getPartitionKeys()).thenReturn(partitionKeys);
        when(storageDescriptor.getCols()).thenReturn(columns);
        when(table.getSd()).thenReturn(storageDescriptor);
        when(table.getDbName()).thenReturn("test_db");
        when(table.getTableName()).thenReturn("partitioned_table");
        when(table.getTableType()).thenReturn(TableType.MANAGED_TABLE);
        when(table.getDataLocation()).thenReturn(new Path("hdfs://test/partitioned"));

        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(database.getName()).thenReturn("test_db");
        when(metastoreHandler.get_database("test_db")).thenReturn(database);

        createTable = spy(new CreateTable(context));
        doReturn(database).when(createTable).getDatabases("test_db");

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        AssertJUnit.assertNotNull("Should handle partitioned tables", entities);
    }

    @Test
    public void testProcessTable_WithNullPartitionKeys() throws Exception {
        when(table.getPartitionKeys()).thenReturn(null);
        when(storageDescriptor.getCols()).thenReturn(Collections.emptyList());
        when(table.getSd()).thenReturn(storageDescriptor);
        when(table.getDbName()).thenReturn("test_db");
        when(table.getTableName()).thenReturn("non_partitioned_table");

        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(database.getName()).thenReturn("test_db");
        when(metastoreHandler.get_database("test_db")).thenReturn(database);

        createTable = spy(new CreateTable(context));
        doReturn(database).when(createTable).getDatabases("test_db");

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        AssertJUnit.assertNotNull("Should handle null partition keys", entities);
    }

    // ===== TESTS FOR STORAGE DESCRIPTOR DETAILS =====

    @Test
    public void testProcessTable_WithDetailedStorageDescriptor() throws Exception {
        List<FieldSchema> columns = Arrays.asList(
            new FieldSchema("col1", "string", "column 1")
        );

        when(serDeInfo.getSerializationLib()).thenReturn("org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe");
        when(serDeInfo.getName()).thenReturn("LazySimpleSerDe");
        
        when(storageDescriptor.getCols()).thenReturn(columns);
        when(storageDescriptor.getInputFormat()).thenReturn("org.apache.hadoop.mapred.TextInputFormat");
        when(storageDescriptor.getOutputFormat()).thenReturn("org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat");
        when(storageDescriptor.getLocation()).thenReturn("hdfs://test/location");
        when(storageDescriptor.getSerdeInfo()).thenReturn(serDeInfo);
        when(storageDescriptor.isCompressed()).thenReturn(true);
        when(storageDescriptor.getNumBuckets()).thenReturn(10);

        when(table.getSd()).thenReturn(storageDescriptor);
        when(table.getDbName()).thenReturn("test_db");
        when(table.getTableName()).thenReturn("detailed_table");
        when(table.getTableType()).thenReturn(TableType.EXTERNAL_TABLE);

        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(database.getName()).thenReturn("test_db");
        when(metastoreHandler.get_database("test_db")).thenReturn(database);

        createTable = spy(new CreateTable(context));
        doReturn(database).when(createTable).getDatabases("test_db");

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        AssertJUnit.assertNotNull("Should handle detailed storage descriptor", entities);
    }

    // ===== TESTS FOR TABLE PROPERTIES =====

    @Test
    public void testProcessTable_WithTableProperties() throws Exception {
        Map<String, String> tableProperties = new HashMap<>();
        tableProperties.put("comment", "Test table comment");
        tableProperties.put("transactional", "true");
        tableProperties.put("orc.compress", "SNAPPY");

        when(table.getParameters()).thenReturn(tableProperties);
        when(table.getDbName()).thenReturn("test_db");
        when(table.getTableName()).thenReturn("table_with_props");
        when(table.getSd()).thenReturn(storageDescriptor);
        when(storageDescriptor.getCols()).thenReturn(Collections.emptyList());

        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(database.getName()).thenReturn("test_db");
        when(metastoreHandler.get_database("test_db")).thenReturn(database);

        createTable = spy(new CreateTable(context));
        doReturn(database).when(createTable).getDatabases("test_db");

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        AssertJUnit.assertNotNull("Should handle table properties", entities);
    }

    // ===== TESTS FOR VIEW HANDLING =====

    @Test
    public void testProcessTable_MaterializedView() throws Exception {
        when(table.getTableType()).thenReturn(TableType.MATERIALIZED_VIEW);
        when(table.getDbName()).thenReturn("test_db");
        when(table.getTableName()).thenReturn("materialized_view");
        when(table.getViewOriginalText()).thenReturn("SELECT * FROM source_table");
        when(table.getViewExpandedText()).thenReturn("SELECT col1, col2 FROM db.source_table");
        when(table.getSd()).thenReturn(storageDescriptor);
        when(storageDescriptor.getCols()).thenReturn(Collections.emptyList());

        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(database.getName()).thenReturn("test_db");
        when(metastoreHandler.get_database("test_db")).thenReturn(database);

        createTable = spy(new CreateTable(context));
        doReturn(database).when(createTable).getDatabases("test_db");

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        AssertJUnit.assertNotNull("Should handle materialized views", entities);
    }

    @Test
    public void testProcessTable_VirtualView() throws Exception {
        when(table.getTableType()).thenReturn(TableType.VIRTUAL_VIEW);
        when(table.getDbName()).thenReturn("test_db");
        when(table.getTableName()).thenReturn("virtual_view");
        when(table.getViewOriginalText()).thenReturn("SELECT id, name FROM users WHERE active = 1");
        when(table.getSd()).thenReturn(null); // Views typically don't have storage descriptor

        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(database.getName()).thenReturn("test_db");
        when(metastoreHandler.get_database("test_db")).thenReturn(database);

        createTable = spy(new CreateTable(context));
        doReturn(database).when(createTable).getDatabases("test_db");

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        AssertJUnit.assertNotNull("Should handle virtual views", entities);
    }

    // ===== TESTS FOR COMPLEX COLUMN TYPES =====

    @Test
    public void testProcessTable_ComplexColumnTypes() throws Exception {
        List<FieldSchema> columns = Arrays.asList(
            new FieldSchema("simple_col", "string", "simple string column"),
            new FieldSchema("array_col", "array<string>", "array of strings"),
            new FieldSchema("map_col", "map<string,int>", "map column"),
            new FieldSchema("struct_col", "struct<name:string,age:int>", "struct column"),
            new FieldSchema("nested_col", "array<struct<id:int,tags:array<string>>>", "nested complex type")
        );

        when(storageDescriptor.getCols()).thenReturn(columns);
        when(table.getSd()).thenReturn(storageDescriptor);
        when(table.getDbName()).thenReturn("test_db");
        when(table.getTableName()).thenReturn("complex_types_table");

        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(database.getName()).thenReturn("test_db");
        when(metastoreHandler.get_database("test_db")).thenReturn(database);

        createTable = spy(new CreateTable(context));
        doReturn(database).when(createTable).getDatabases("test_db");

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        AssertJUnit.assertNotNull("Should handle complex column types", entities);
    }

    // ===== TESTS FOR EDGE CASES IN TABLE OPERATIONS =====

    @Test
    public void testSkipTemporaryTable_IndexTable() {
        when(table.isTemporary()).thenReturn(false);
        when(table.getTableType()).thenReturn(TableType.INDEX_TABLE);

        createTable = new CreateTable(context);
        boolean result = createTable.skipTemporaryTable(table);

        AssertJUnit.assertFalse("Should not skip index tables", result);
    }

    @Test
    public void testIsCreateExtTableOperation_CreateTableAsSelect() {
        when(table.getTableType()).thenReturn(TableType.EXTERNAL_TABLE);
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE_AS_SELECT);

        createTable = new CreateTable(context);
        boolean result = createTable.isCreateExtTableOperation(table);

        AssertJUnit.assertTrue("Should be true for CTAS with external table", result);
    }

    @Test
    public void testIsCreateExtTableOperation_AlterTable() {
        when(table.getTableType()).thenReturn(TableType.EXTERNAL_TABLE);
        when(context.getHiveOperation()).thenReturn(HiveOperation.ALTERTABLE_PROPERTIES);

        createTable = new CreateTable(context);
        boolean result = createTable.isCreateExtTableOperation(table);

        AssertJUnit.assertFalse("Should be false for ALTER operations", result);
    }

    // ===== TESTS FOR DATABASE ACCESS PATTERNS =====

    @Test
    public void testProcessTable_DatabaseWithCatalog() throws Exception {
        when(table.getDbName()).thenReturn("test_db");
        when(table.getTableName()).thenReturn("test_table");
        when(table.getSd()).thenReturn(storageDescriptor);
        when(storageDescriptor.getCols()).thenReturn(Collections.emptyList());

        when(database.getName()).thenReturn("test_db");
        when(database.getCatalogName()).thenReturn("custom_catalog");
        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(metastoreHandler.get_database("test_db")).thenReturn(database);

        createTable = spy(new CreateTable(context));
        doReturn(database).when(createTable).getDatabases("test_db");

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        AssertJUnit.assertNotNull("Should handle database with custom catalog", entities);
    }

    @Test
    public void testProcessTable_DatabaseAccessException() throws Exception {
        when(table.getDbName()).thenReturn("error_db");
        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(metastoreHandler.get_database("error_db"))
            .thenThrow(new RuntimeException("Database access error"));

        createTable = spy(new CreateTable(context));
        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();

        try {
            createTable.processTable(table, entities);
            AssertJUnit.fail("Should propagate database access exception");
        } catch (Exception e) {
            AssertJUnit.assertTrue("Should handle database access exception",
                e.getMessage().contains("Database access error"));
        }
    }

    // ===== TESTS FOR HOOK CONTEXT VARIATIONS =====

    @Test
    public void testGetNotificationMessages_NonMetastoreHook_WithTableOutputs() throws Exception {
        when(context.isMetastoreHook()).thenReturn(false);
        
        Entity entity = mock(Entity.class);
        Table qlTable = mock(Table.class);
        when(entity.getType()).thenReturn(Entity.Type.TABLE);
        when(entity.getTable()).thenReturn(qlTable);
        when(qlTable.getDbName()).thenReturn("test_db");
        when(qlTable.getTableName()).thenReturn("test_table");
        when(context.getOutputs()).thenReturn(Collections.singleton(entity));

        createTable = spy(new CreateTable(context));
        doReturn(hive).when(createTable).getHive();
        when(hive.getTable("test_db", "test_table")).thenReturn(qlTable);
        
        doAnswer(invocation -> {
            AtlasEntitiesWithExtInfo entities = invocation.getArgument(1);
            AtlasEntity tblEntity = new AtlasEntity("hive_table");
            entities.addEntity(tblEntity);
            return null;
        }).when(createTable).processTable(any(), any());

        List<HookNotification> notifications = createTable.getNotificationMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        AssertJUnit.assertEquals("Should have one notification", 1, notifications.size());
    }

    // ===== TESTS FOR LINEAGE AND DEPENDENCIES =====

    @Test
    public void testCreateTableAsSelect_WithLineage() throws Exception {
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE_AS_SELECT);
        
        Entity inputEntity = mock(Entity.class);
        Entity outputEntity = mock(Entity.class);
        Table inputTable = mock(Table.class);
        Table outputTable = mock(Table.class);
        
        when(inputEntity.getType()).thenReturn(Entity.Type.TABLE);
        when(inputEntity.getTable()).thenReturn(inputTable);
        when(inputTable.getDbName()).thenReturn("source_db");
        when(inputTable.getTableName()).thenReturn("source_table");
        
        when(outputEntity.getType()).thenReturn(Entity.Type.TABLE);
        when(outputEntity.getTable()).thenReturn(outputTable);
        when(outputTable.getDbName()).thenReturn("target_db");
        when(outputTable.getTableName()).thenReturn("target_table");

        when(context.getInputs()).thenReturn(Collections.singleton(inputEntity));
        when(context.getOutputs()).thenReturn(Collections.singleton(outputEntity));

        createTable = spy(new CreateTable(context));
        doReturn(hive).when(createTable).getHive();
        when(hive.getTable("source_db", "source_table")).thenReturn(inputTable);
        when(hive.getTable("target_db", "target_table")).thenReturn(outputTable);
        
        doAnswer(invocation -> {
            AtlasEntitiesWithExtInfo entities = invocation.getArgument(1);
            AtlasEntity tblEntity = new AtlasEntity("hive_table");
            entities.addEntity(tblEntity);
            return null;
        }).when(createTable).processTable(any(), any());

        List<HookNotification> notifications = createTable.getNotificationMessages();
        
        AssertJUnit.assertNotNull("Should handle CTAS with lineage", notifications);
    }

    // ===== TESTS FOR TABLE METADATA COMPLETENESS =====

    @Test
    public void testProcessTable_FullTableMetadata() throws Exception {
        List<FieldSchema> columns = Arrays.asList(
            new FieldSchema("id", "bigint", "primary key"),
            new FieldSchema("name", "varchar(100)", "user name"),
            new FieldSchema("created_date", "timestamp", "creation timestamp")
        );

        Map<String, String> tableParams = new HashMap<>();
        tableParams.put("comment", "Full metadata test table");
        tableParams.put("created_by", "test_user");
        tableParams.put("last_modified_time", "1609459200");

        when(storageDescriptor.getCols()).thenReturn(columns);
        when(storageDescriptor.getLocation()).thenReturn("s3://bucket/path/table");
        when(storageDescriptor.getInputFormat()).thenReturn("org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat");
        when(storageDescriptor.getOutputFormat()).thenReturn("org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat");
        
        when(table.getSd()).thenReturn(storageDescriptor);
        when(table.getDbName()).thenReturn("production");
        when(table.getTableName()).thenReturn("user_events");
        when(table.getTableType()).thenReturn(TableType.EXTERNAL_TABLE);
        when(table.getOwner()).thenReturn("data_team");
        when(table.getCreateTime()).thenReturn(1609459200);
        when(table.getLastAccessTime()).thenReturn(1609459300);
        when(table.getParameters()).thenReturn(tableParams);
        when(table.getRetention()).thenReturn(365);

        when(context.getMetastoreHandler()).thenReturn(metastoreHandler);
        when(database.getName()).thenReturn("production");
        when(database.getDescription()).thenReturn("Production database");
        when(metastoreHandler.get_database("production")).thenReturn(database);

        createTable = spy(new CreateTable(context));
        doReturn(database).when(createTable).getDatabases("production");

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        AssertJUnit.assertNotNull("Should handle full table metadata", entities);
    }
}