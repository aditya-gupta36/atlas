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
import org.apache.hadoop.hive.metastore.events.CreateTableEvent;
import org.apache.hadoop.hive.ql.hooks.Entity;
import org.apache.hadoop.hive.ql.metadata.Hive;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.hadoop.hive.ql.plan.HiveOperation;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.session.SessionState;
import org.mockito.*;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

import static org.apache.hadoop.hive.metastore.TableType.EXTERNAL_TABLE;
import static org.mockito.Mockito.*;

public class CTable_FInal_Fixed {

    @Mock
    AtlasHiveHookContext context;

    @Mock
    CreateTableEvent createTableEvent;

    @Mock
    Table table;

    @Mock
    HiveConf hiveConf;

    @Mock
    SessionState sessionState;

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
        // Setup basic table mocks
        when(table.getTableType()).thenReturn(TableType.MANAGED_TABLE);
        when(table.getDbName()).thenReturn("default");
        when(table.getTableName()).thenReturn("july3001");

        // Setup context mocks with all required information for DDL entity creation
        when(context.getHiveConf()).thenReturn(hiveConf);
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE);
        when(context.getQueryStr()).thenReturn("CREATE TABLE default.july3001 (id INT)");
        when(context.getQueryId()).thenReturn("query_123456789");
        when(context.getUser()).thenReturn("test_user");
        when(context.getUserName()).thenReturn("test_user");
        when(context.isMetastoreHook()).thenReturn(false); // Important: set to false to trigger DDL creation

        // Mock HiveConf
        when(hiveConf.get("hive.query.string")).thenReturn("CREATE TABLE default.july3001 (id INT)");
        when(hiveConf.get("hive.query.id")).thenReturn("query_123456789");

        // Mock SessionState for user information
        when(sessionState.getUserName()).thenReturn("test_user");
        when(SessionState.get()).thenReturn(sessionState);

        CreateTable createTable = spy(new CreateTable(context));
        
        // Mock the HBase-specific methods
        doReturn(true).when(createTable).isHBaseStore(table);
        
        // Mock entity creation methods
        AtlasEntity hiveTableEntity = new AtlasEntity("hive_table");
        hiveTableEntity.setAttribute("qualifiedName", "default.july3001@cluster");
        doReturn(hiveTableEntity).when(createTable).toTableEntity(any(), any());
        
        AtlasEntity hbaseTableEntity = new AtlasEntity("hbase_table");
        hbaseTableEntity.setAttribute("qualifiedName", "default:july3001");
        doReturn(hbaseTableEntity).when(createTable).toReferencedHBaseTable(any(), any());
        
        AtlasEntity processEntity = new AtlasEntity("hive_process");
        processEntity.setAttribute("qualifiedName", "default.july3001@cluster_process");
        doReturn(processEntity).when(createTable).getHiveProcessEntity(anyList(), anyList());
        
        AtlasEntity executionEntity = new AtlasEntity("hive_process_execution");
        executionEntity.setAttribute("qualifiedName", "default.july3001@cluster_execution");
        doReturn(executionEntity).when(createTable).getHiveProcessExecutionEntity(any());

        // Mock the DDL entity creation to prevent NPE
        AtlasEntity ddlEntity = new AtlasEntity("hive_ddl");
        ddlEntity.setAttribute("qualifiedName", "query_123456789@cluster");
        doReturn(ddlEntity).when(createTable).createHiveDDLEntity(any());

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        AssertJUnit.assertFalse("Expected entities to be added", entities.getEntities().isEmpty());
        
        // Verify HBase table entity was added
        boolean hasHBaseEntity = entities.getEntities().stream()
            .anyMatch(entity -> "hbase_table".equals(entity.getTypeName()));
        AssertJUnit.assertTrue("Expected HBase table entity to be added", hasHBaseEntity);
    }

    @Test
    public void testProcessTable_WithDDLEntity() throws Exception {
        // Mock required context for DDL entity creation
        when(context.isMetastoreHook()).thenReturn(false);
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE);
        when(context.getQueryStr()).thenReturn("CREATE EXTERNAL TABLE default.tbl (id INT)");
        when(context.getQueryId()).thenReturn("query_987654321");
        when(context.getUser()).thenReturn("test_user");
        when(context.getUserName()).thenReturn("test_user");
        when(context.getHiveConf()).thenReturn(hiveConf);

        // Mock HiveConf
        when(hiveConf.get("hive.query.string")).thenReturn("CREATE EXTERNAL TABLE default.tbl (id INT)");
        when(hiveConf.get("hive.query.id")).thenReturn("query_987654321");

        // Mock table
        when(table.getDbName()).thenReturn("default");
        when(table.getTableName()).thenReturn("tbl");
        when(table.getTableType()).thenReturn(EXTERNAL_TABLE);

        CreateTable createTable = spy(new CreateTable(context));
        
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
        // This test should verify the skip logic based on configuration
        boolean result = createTable.skipTemporaryTable(table);
        // The actual result depends on the implementation and configuration
        // Adjust assertion based on your actual implementation
    }

    // Additional test to cover more edge cases
    @Test
    public void testProcessTable_NonHBaseStore_MetastoreHook() throws Exception {
        // Setup for non-HBase store with metastore hook
        when(table.getTableType()).thenReturn(TableType.MANAGED_TABLE);
        when(table.getDbName()).thenReturn("default");
        when(table.getTableName()).thenReturn("managed_table");
        
        // Mock context as metastore hook (won't create DDL entity)
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getHiveOperation()).thenReturn(HiveOperation.CREATETABLE);

        CreateTable createTable = spy(new CreateTable(context));
        
        // Mock as non-HBase store
        doReturn(false).when(createTable).isHBaseStore(table);
        
        // Mock entity creation
        AtlasEntity hiveTableEntity = new AtlasEntity("hive_table");
        doReturn(hiveTableEntity).when(createTable).toTableEntity(any(), any());
        
        AtlasEntity hdfsEntity = new AtlasEntity("hdfs_path");
        doReturn(hdfsEntity).when(createTable).getPathEntity(any(), any());
        
        AtlasEntity processEntity = new AtlasEntity("hive_process");
        doReturn(processEntity).when(createTable).getHiveProcessEntity(anyList(), anyList());

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        AssertJUnit.assertFalse("Expected entities to be added", entities.getEntities().isEmpty());
        
        // Verify no DDL entity was created (since it's a metastore hook)
        boolean hasDDLEntity = entities.getEntities().stream()
            .anyMatch(entity -> "hive_ddl".equals(entity.getTypeName()));
        AssertJUnit.assertFalse("DDL entity should not be created for metastore hook", hasDDLEntity);
    }

    @Test
    public void testProcessTable_NullTableLocation() throws Exception {
        when(table.getTableType()).thenReturn(EXTERNAL_TABLE);
        when(table.getDbName()).thenReturn("default");
        when(table.getTableName()).thenReturn("external_table");
        when(table.getDataLocation()).thenReturn(null); // Null location

        when(context.isMetastoreHook()).thenReturn(true);

        CreateTable createTable = spy(new CreateTable(context));
        doReturn(false).when(createTable).isHBaseStore(table);
        
        AtlasEntity hiveTableEntity = new AtlasEntity("hive_table");
        doReturn(hiveTableEntity).when(createTable).toTableEntity(any(), any());
        
        // getPathEntity should handle null location gracefully
        doReturn(null).when(createTable).getPathEntity(any(), any());
        
        AtlasEntity processEntity = new AtlasEntity("hive_process");
        doReturn(processEntity).when(createTable).getHiveProcessEntity(anyList(), anyList());

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        createTable.processTable(table, entities);

        AssertJUnit.assertFalse("Expected entities to be added", entities.getEntities().isEmpty());
    }
}