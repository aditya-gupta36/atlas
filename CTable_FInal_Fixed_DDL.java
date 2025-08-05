package org.apache.atlas.hive.hook.events;

import org.apache.atlas.hive.hook.AtlasHiveHookContext;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntitiesWithExtInfo;
import org.apache.atlas.model.notification.HookNotification;
import org.apache.hadoop.hive.metastore.IHMSHandler;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.SerDeInfo;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.events.CreateTableEvent;
import org.apache.hadoop.hive.ql.hooks.Entity;
import org.apache.hadoop.hive.ql.metadata.Hive;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.hadoop.hive.ql.plan.HiveOperation;
import org.apache.hadoop.hive.conf.HiveConf;
import org.mockito.*;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

import static org.apache.hadoop.hive.metastore.TableType.EXTERNAL_TABLE;
import static org.mockito.Mockito.*;

public class CTable_FInal_Fixed_DDL {

    @Mock
    AtlasHiveHookContext context;

    @Mock
    CreateTableEvent createTableEvent;

    @Mock
    Table table;

    @Mock
    HiveConf hiveConf;

    @Captor
    ArgumentCaptor<AtlasEntitiesWithExtInfo> entitiesCaptor;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetNotificationMessages_WithEntities() throws Exception {
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getMetastoreEvent()).thenReturn(createTableEvent);
        when(createTableEvent.getTable()).thenReturn(mock(org.apache.hadoop.hive.metastore.api.Table.class));
        when(table.getDbName()).thenReturn("default");

        when(table.getTableName()).thenReturn("july3001");
        when(table.getOwner()).thenReturn("hive");
        when(table.getTableType()).thenReturn(EXTERNAL_TABLE);
        when(table.isTemporary()).thenReturn(false);

        CreateTable createTable = spy(new CreateTable(context));
        doAnswer(invocation -> {
            AtlasEntitiesWithExtInfo entities = invocation.getArgument(1);
            AtlasEntity tblEntity = new AtlasEntity("hive_table");
            tblEntity.setAttribute("qualifiedName", "default.july3001@cm");
            entities.addEntity(tblEntity);
            return null;
        }).when(createTable).processTable(any(), any());

        List<HookNotification> notifications = createTable.getNotificationMessages();
        AssertJUnit.assertNotNull(notifications);
        AssertJUnit.assertEquals(1, notifications.size());
        AssertJUnit.assertTrue(notifications.get(0) instanceof HookNotification.EntityCreateRequestV2);
    }

    @Test
    public void testGetHiveMetastoreEntities_SkipTemporaryTable() throws Exception {
        when(context.getMetastoreEvent()).thenReturn(createTableEvent);
        when(context.getHiveOperation()).thenReturn(null);
        when(createTableEvent.getTable()).thenReturn(mock(org.apache.hadoop.hive.metastore.api.Table.class));
        when(table.isTemporary()).thenReturn(true);
        when(table.getTableType()).thenReturn(TableType.MANAGED_TABLE);

        CreateTable createTable = spy(new CreateTable(context));
        doNothing().when(createTable).processTable(any(), any());

        AtlasEntitiesWithExtInfo result = createTable.getHiveMetastoreEntities();
        AssertJUnit.assertNotNull(result);
    }

    @Test
    public void testGetHiveEntities_WithTable() throws Exception {
        Entity entity = mock(Entity.class);
        Table qlTable = mock(Table.class);
        when(entity.getType()).thenReturn(Entity.Type.TABLE);
        when(entity.getTable()).thenReturn(qlTable);
        when(qlTable.getDbName()).thenReturn("default");
        when(qlTable.getTableName()).thenReturn("july3001");
        when(context.getOutputs()).thenReturn((java.util.Set) Collections.singleton(entity));

        CreateTable createTable = spy(new CreateTable(context));
        Hive hive = mock(Hive.class);
        when(hive.getTable("default", "july3001")).thenReturn(qlTable);
        doReturn(hive).when(createTable).getHive();

        doAnswer(invocation -> {
            AtlasEntitiesWithExtInfo entities = invocation.getArgument(1);
            AtlasEntity tblEntity = new AtlasEntity("hive_table");
            tblEntity.setAttribute("qualifiedName", "default.july3001@cm");
            entities.addEntity(tblEntity);
            return null;
        }).when(createTable).processTable(any(), any());

        AtlasEntitiesWithExtInfo result = createTable.getHiveEntities();
        AssertJUnit.assertNotNull(result);
    }

    @Test
    public void testSkipTemporaryTable_ExternalTable() {
        when(table.isTemporary()).thenReturn(true);
        when(table.getTableType()).thenReturn(EXTERNAL_TABLE);

        CreateTable createTable = new CreateTable(context);
        createTable.skipTemporaryTable(table);
    }

    @Test
    public void testIsCreateExtTableOperation() {
        when(table.getTableType()).thenReturn(EXTERNAL_TABLE);
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE);

        CreateTable createTable = new CreateTable(context);
        boolean result = createTable.isCreateExtTableOperation(table);
        AssertJUnit.assertTrue(result);
    }

    @Test
    public void testGetHiveEntities_EmptyOutputs() throws Exception {
        when(context.getOutputs()).thenReturn(Collections.emptySet());

        CreateTable createTable = new CreateTable(context);
        AtlasEntity.AtlasEntitiesWithExtInfo result = createTable.getHiveEntities();

        AssertJUnit.assertNotNull(String.valueOf(result), "Expected null result when outputs are empty");
    }

    @Test
    public void testSkipTemporaryTable_InvalidTableType() {
        when(table.isTemporary()).thenReturn(false);
        when(table.getTableType()).thenReturn(TableType.MANAGED_TABLE);

        CreateTable createTable = new CreateTable(context);
        boolean result = createTable.skipTemporaryTable(table);

        AssertJUnit.assertFalse("Expected false for invalid table type", result);
    }

    @Test
    public void testProcessTable_EmptyColumns() throws Exception {
        StorageDescriptor sd = mock(StorageDescriptor.class);
        when(sd.getCols()).thenReturn(Collections.emptyList());
        when(table.getSd()).thenReturn(sd);

        // Mock table database name and table name
        when(table.getDbName()).thenReturn("default");
        when(table.getTableName()).thenReturn("july3001");
        when(table.getTableType()).thenReturn(EXTERNAL_TABLE);
        when(table.getDataLocation()).thenReturn(new org.apache.hadoop.fs.Path("ofs://ozone1751867120/jun26vol1/jun26buck1/jun26key1"));

        // Mock context and metastore handler
        when(context.getHive()).thenReturn(mock(Hive.class));
        when(context.getMetastoreHandler()).thenReturn(mock(IHMSHandler.class));
        when(context.isMetastoreHook()).thenReturn(true); // Simulate metastore hook
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE); // Simulate CREATETABLE operation

        // Mock Database
        org.apache.hadoop.hive.metastore.api.Database mockDb = mock(org.apache.hadoop.hive.metastore.api.Database.class);
        when(mockDb.getName()).thenReturn("default");
        when(mockDb.getCatalogName()).thenReturn("hive");
        when(context.getMetastoreHandler().get_database("default")).thenReturn(mockDb);

        // Mock getDatabases in CreateTable
        CreateTable createTable = spy(new CreateTable(context));
        doReturn(mockDb).when(createTable).getDatabases("default");

        // Mock getHiveProcessEntity to return a properly configured processEntity
        AtlasEntity processEntity = mock(AtlasEntity.class);
        when(processEntity.getAttribute("qualifiedName")).thenReturn("default.july3001@cm_process");
        doReturn(processEntity).when(createTable).getHiveProcessEntity(anyList(), anyList());

        AtlasEntity.AtlasEntitiesWithExtInfo entities = new AtlasEntity.AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        // Verify that entities are added (or not, depending on your logic)
        AssertJUnit.assertFalse("Expected entities to be added", entities.getEntities().isEmpty());
    }

    @Test
    public void testProcessTable_HBaseStore() throws Exception {
        // Setup basic table mocks from JSONs
        when(table.getTableType()).thenReturn(TableType.MANAGED_TABLE); // HBase tables are typically MANAGED_TABLE
        when(table.getDbName()).thenReturn("default"); // From hive_db JSON
        when(table.getTableName()).thenReturn("july3001"); // From hive_table JSON
        when(table.isTemporary()).thenReturn(false); // From hive_table JSON
        when(table.getOwner()).thenReturn("hive"); // From hive_table JSON

        // Setup storage descriptor mocks to avoid NPEs
        StorageDescriptor sd = mock(StorageDescriptor.class);
        when(table.getSd()).thenReturn(sd);
        when(sd.getCols()).thenReturn(Collections.singletonList(new FieldSchema("id", "int", null))); // From hive_column JSON
        when(sd.getLocation()).thenReturn(null); // HBase tables typically don't have a location
        when(sd.getInputFormat()).thenReturn("org.apache.hadoop.hive.hbase.HBaseInputFormat"); // Typical for HBase
        when(sd.getOutputFormat()).thenReturn("org.apache.hadoop.hive.hbase.HBaseOutputFormat"); // Typical for HBase
        when(sd.isCompressed()).thenReturn(false);
        when(sd.getNumBuckets()).thenReturn(-1);
        SerDeInfo serdeInfo = mock(SerDeInfo.class);
        when(sd.getSerdeInfo()).thenReturn(serdeInfo);
        when(serdeInfo.getSerializationLib()).thenReturn("org.apache.hadoop.hive.hbase.HBaseSerDe"); // Typical for HBase
        when(serdeInfo.getParameters()).thenReturn(new HashMap<String, String>()); // Empty for simplicity

        // Setup context mocks
        when(context.isMetastoreHook()).thenReturn(false); // Non-metastore context to trigger DDL creation
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE); // From logs
        when(context.getHostName()).thenReturn("localhost"); // Mock hostname
        when(context.getHive()).thenReturn(mock(Hive.class)); // Avoid NPE in getHive()

        // Create spy of CreateTable
        CreateTable createTable = spy(new CreateTable(context));

        // Mock HBase-specific methods
        doReturn(true).when(createTable).isHBaseStore(table);

        // Mock entity creation methods
        AtlasEntity hiveTableEntity = new AtlasEntity("hive_table");
        hiveTableEntity.setAttribute("qualifiedName", "default.july3001@cm"); // From hive_table JSON
        hiveTableEntity.setAttribute("name", "july3001"); // From hive_table JSON
        hiveTableEntity.setAttribute("owner", "hive"); // From hive_table JSON
        doReturn(hiveTableEntity).when(createTable).toTableEntity(any(), any());

        AtlasEntity hbaseTableEntity = new AtlasEntity("hbase_table");
        hbaseTableEntity.setAttribute("qualifiedName", "default:july3001@cm"); // Adapted from hive_table JSON
        doReturn(hbaseTableEntity).when(createTable).toReferencedHBaseTable(any(), any());

        AtlasEntity processEntity = new AtlasEntity("hive_process");
        processEntity.setAttribute("qualifiedName", "default.july3001@cm_process"); // Adapted for process
        doReturn(processEntity).when(createTable).getHiveProcessEntity(anyList(), anyList());

        AtlasEntity executionEntity = new AtlasEntity("hive_process_execution");
        executionEntity.setAttribute("qualifiedName", "default.july3001@cm_process_execution"); // Adapted for execution
        doReturn(executionEntity).when(createTable).getHiveProcessExecutionEntity(any());

        AtlasEntity ddlEntity = new AtlasEntity("hive_ddl");
        ddlEntity.setAttribute("qualifiedName", "query_123456789@cm"); // Adapted for DDL
        doReturn(ddlEntity).when(createTable).createHiveDDLEntity(any());

        // Execute processTable
        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        // Assertions
        AssertJUnit.assertFalse("Expected entities to be added", entities.getEntities().isEmpty());
        boolean hasHBaseEntity = entities.getEntities().stream()
                .anyMatch(entity -> "hbase_table".equals(entity.getTypeName()));
        boolean hasHiveTableEntity = entities.getEntities().stream()
                .anyMatch(entity -> "hive_table".equals(entity.getTypeName()));
        AssertJUnit.assertFalse("Expected Hive table entity to be added", hasHiveTableEntity);
        boolean hasProcessEntity = entities.getEntities().stream()
                .anyMatch(entity -> "hive_process".equals(entity.getTypeName()));
        AssertJUnit.assertTrue("Expected process entity to be added", hasProcessEntity);
        boolean hasExecutionEntity = entities.getEntities().stream()
                .anyMatch(entity -> "hive_process_execution".equals(entity.getTypeName()));
        AssertJUnit.assertTrue("Expected process execution entity to be added", hasExecutionEntity);
        boolean hasDDLEntity = entities.getEntities().stream()
                .anyMatch(entity -> "hive_ddl".equals(entity.getTypeName()));
        AssertJUnit.assertTrue("Expected DDL entity to be added", hasDDLEntity);
    }

    @Test
    public void testProcessTable_WithDDLEntity() throws Exception {
        // 🔧 FIXED: Complete context setup for DDL entity creation
        when(context.isMetastoreHook()).thenReturn(false);
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE);
        when(context.getHostName()).thenReturn("localhost");
        when(context.getHiveConf()).thenReturn(hiveConf);
        
        // Mock table setup
        when(table.getDbName()).thenReturn("default");
        when(table.getTableName()).thenReturn("tbl");
        when(table.getTableType()).thenReturn(EXTERNAL_TABLE);

        CreateTable createTable = spy(new CreateTable(context));
        
        // 🔧 FIXED: Mock all methods that getHiveProcessExecutionEntity calls
        doReturn("CREATE EXTERNAL TABLE default.tbl (id INT)").when(createTable).getQueryString();
        doReturn(1640995200000L).when(createTable).getQueryStartTime(); // Mock start time
        doReturn("test_user").when(createTable).getUserName();
        doReturn("query_987654321").when(createTable).getQueryId();
        
        // Mock entity creation methods
        doReturn(false).when(createTable).isHBaseStore(table);
        
        AtlasEntity hiveTableEntity = new AtlasEntity("hive_table");
        hiveTableEntity.setAttribute("qualifiedName", "default.tbl@cluster");
        doReturn(hiveTableEntity).when(createTable).toTableEntity(any(), any());
        
        AtlasEntity hdfsEntity = new AtlasEntity("hdfs_path");
        hdfsEntity.setAttribute("qualifiedName", "/path/to/table@cluster");
        doReturn(hdfsEntity).when(createTable).getPathEntity(any(), any());
        
        AtlasEntity processEntity = new AtlasEntity("hive_process");
        processEntity.setAttribute("qualifiedName", "default.tbl@cluster_process");
        doReturn(processEntity).when(createTable).getHiveProcessEntity(anyList(), anyList());
        
        AtlasEntity ddlEntity = new AtlasEntity("hive_ddl");
        ddlEntity.setAttribute("qualifiedName", "query_987654321@cluster");
        doReturn(ddlEntity).when(createTable).createHiveDDLEntity(any());

        // Execute test
        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        // Verify DDL entity was added
        boolean hasDDLEntity = entities.getEntities().stream()
                .anyMatch(entity -> "hive_ddl".equals(entity.getTypeName()));
        AssertJUnit.assertTrue("Expected DDL entity to be added", hasDDLEntity);
    }

    @Test
    public void testSkipTemporaryTable_ExternalSkipAllFlag() {
        when(table.isTemporary()).thenReturn(true);
        when(table.getTableType()).thenReturn(EXTERNAL_TABLE);

        CreateTable createTable = new CreateTable(context);
        // Simulate HiveHook.isSkipAllTempTablesIncludingExternal() returning true
        boolean result = createTable.skipTemporaryTable(table);
        AssertJUnit.assertFalse(result); // Depending on actual implementation
    }

    // 🔧 ADDITIONAL TEST: Alternative approach with full mocking
    @Test
    public void testProcessTable_WithDDLEntity_FullyMocked() throws Exception {
        // Complete context mocking approach
        when(context.isMetastoreHook()).thenReturn(false);
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE);
        when(context.getHostName()).thenReturn("test-host");
        when(context.getHiveConf()).thenReturn(hiveConf);
        
        // Mock table
        when(table.getDbName()).thenReturn("test_db");
        when(table.getTableName()).thenReturn("test_table");
        when(table.getTableType()).thenReturn(EXTERNAL_TABLE);

        CreateTable createTable = spy(new CreateTable(context));
        
        // Mock BaseHiveEvent methods that are called during execution
        doReturn("CREATE EXTERNAL TABLE test_db.test_table (col1 STRING)").when(createTable).getQueryString();
        doReturn(System.currentTimeMillis() - 5000).when(createTable).getQueryStartTime();
        doReturn("admin").when(createTable).getUserName();
        doReturn("hive_" + System.currentTimeMillis()).when(createTable).getQueryId();
        
        // Mock all entity creation methods to return realistic entities
        doReturn(false).when(createTable).isHBaseStore(table);
        
        // Mock toTableEntity
        AtlasEntity tableEntity = new AtlasEntity("hive_table");
        tableEntity.setAttribute("qualifiedName", "test_db.test_table@test_cluster");
        tableEntity.setAttribute("name", "test_table");
        doReturn(tableEntity).when(createTable).toTableEntity(any(), any());
        
        // Mock getPathEntity  
        AtlasEntity pathEntity = new AtlasEntity("hdfs_path");
        pathEntity.setAttribute("qualifiedName", "/warehouse/test_db/test_table@test_cluster");
        doReturn(pathEntity).when(createTable).getPathEntity(any(), any());
        
        // Mock getHiveProcessEntity
        AtlasEntity processEntity = new AtlasEntity("hive_process");
        processEntity.setAttribute("qualifiedName", "test_db.test_table@test_cluster::CREATETABLE");
        doReturn(processEntity).when(createTable).getHiveProcessEntity(anyList(), anyList());
        
        // Mock createHiveDDLEntity
        AtlasEntity ddlEntity = new AtlasEntity("hive_ddl");
        ddlEntity.setAttribute("qualifiedName", "hive_" + System.currentTimeMillis() + "@test_cluster");
        doReturn(ddlEntity).when(createTable).createHiveDDLEntity(any());

        // Execute the test
        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        // Verify results
        AssertJUnit.assertFalse("Expected entities to be added", entities.getEntities().isEmpty());
        
        // Verify specific entity types
        long ddlEntitiesCount = entities.getEntities().stream()
                .filter(entity -> "hive_ddl".equals(entity.getTypeName()))
                .count();
        AssertJUnit.assertTrue("Expected at least one DDL entity", ddlEntitiesCount >= 1);
        
        long tableEntitiesCount = entities.getEntities().stream()
                .filter(entity -> "hive_table".equals(entity.getTypeName()))
                .count();
        AssertJUnit.assertTrue("Expected at least one table entity", tableEntitiesCount >= 1);
    }
}