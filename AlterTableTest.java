package org.apache.atlas.hive.hook.events;

import org.apache.atlas.hive.hook.AtlasHiveHookContext;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntitiesWithExtInfo;
import org.apache.atlas.model.notification.HookNotification;
import org.apache.atlas.model.notification.HookNotification.EntityUpdateRequestV2;
import org.apache.hadoop.hive.metastore.IHMSHandler;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.SerDeInfo;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.events.AlterTableEvent;
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
import static org.apache.hadoop.hive.metastore.TableType.MANAGED_TABLE;
import static org.mockito.Mockito.*;

public class AlterTableTest {

    @Mock
    AtlasHiveHookContext context;

    @Mock
    AlterTableEvent alterTableEvent;

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

    // ========== CONSTRUCTOR TESTS ==========

    @Test
    public void testConstructor() {
        AlterTable alterTable = new AlterTable(context);
        AssertJUnit.assertNotNull("AlterTable should be instantiated", alterTable);
        // Verify it extends CreateTable
        AssertJUnit.assertTrue("AlterTable should extend CreateTable", alterTable instanceof CreateTable);
    }

    @Test
    public void testConstructorWithNullContext() {
        // This should not throw exception as it calls super(context)
        AlterTable alterTable = new AlterTable(null);
        AssertJUnit.assertNotNull("AlterTable should be instantiated even with null context", alterTable);
    }

    // ========== getNotificationMessages() TESTS ==========

    @Test
    public void testGetNotificationMessages_MetastoreHook_WithEntities() throws Exception {
        // Setup metastore hook scenario
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getMetastoreEvent()).thenReturn(alterTableEvent);
        when(alterTableEvent.getOldTable()).thenReturn(mock(org.apache.hadoop.hive.metastore.api.Table.class));
        when(alterTableEvent.getNewTable()).thenReturn(mock(org.apache.hadoop.hive.metastore.api.Table.class));

        AlterTable alterTable = spy(new AlterTable(context));
        
        // Mock getHiveMetastoreEntities to return entities
        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        AtlasEntity tableEntity = new AtlasEntity("hive_table");
        tableEntity.setAttribute("qualifiedName", "default.test_table@cluster");
        entities.addEntity(tableEntity);
        
        doReturn(entities).when(alterTable).getHiveMetastoreEntities();
        doReturn("test_user").when(alterTable).getUserName();

        List<HookNotification> notifications = alterTable.getNotificationMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        AssertJUnit.assertEquals("Should have exactly one notification", 1, notifications.size());
        AssertJUnit.assertTrue("Notification should be EntityUpdateRequestV2", 
                notifications.get(0) instanceof EntityUpdateRequestV2);
        
        EntityUpdateRequestV2 updateRequest = (EntityUpdateRequestV2) notifications.get(0);
        AssertJUnit.assertEquals("User should match", "test_user", updateRequest.getUser());
        AssertJUnit.assertNotNull("Entities should not be null", updateRequest.getEntities());
        AssertJUnit.assertEquals("Should have one entity", 1, updateRequest.getEntities().getEntities().size());
    }

    @Test
    public void testGetNotificationMessages_HiveEntities_WithEntities() throws Exception {
        // Setup non-metastore hook scenario
        when(context.isMetastoreHook()).thenReturn(false);
        when(context.getOutputs()).thenReturn(createMockOutputs());

        AlterTable alterTable = spy(new AlterTable(context));
        
        // Mock getHiveEntities to return entities
        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        AtlasEntity tableEntity = new AtlasEntity("hive_table");
        tableEntity.setAttribute("qualifiedName", "default.altered_table@cluster");
        entities.addEntity(tableEntity);
        
        doReturn(entities).when(alterTable).getHiveEntities();
        doReturn("admin_user").when(alterTable).getUserName();

        List<HookNotification> notifications = alterTable.getNotificationMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        AssertJUnit.assertEquals("Should have exactly one notification", 1, notifications.size());
        AssertJUnit.assertTrue("Notification should be EntityUpdateRequestV2", 
                notifications.get(0) instanceof EntityUpdateRequestV2);
        
        EntityUpdateRequestV2 updateRequest = (EntityUpdateRequestV2) notifications.get(0);
        AssertJUnit.assertEquals("User should match", "admin_user", updateRequest.getUser());
    }

    @Test
    public void testGetNotificationMessages_EmptyEntities() throws Exception {
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getMetastoreEvent()).thenReturn(alterTableEvent);

        AlterTable alterTable = spy(new AlterTable(context));
        
        // Mock empty entities
        AtlasEntitiesWithExtInfo emptyEntities = new AtlasEntitiesWithExtInfo();
        doReturn(emptyEntities).when(alterTable).getHiveMetastoreEntities();

        List<HookNotification> notifications = alterTable.getNotificationMessages();

        AssertJUnit.assertNull("Notifications should be null for empty entities", notifications);
    }

    @Test
    public void testGetNotificationMessages_NullEntities() throws Exception {
        when(context.isMetastoreHook()).thenReturn(false);

        AlterTable alterTable = spy(new AlterTable(context));
        doReturn(null).when(alterTable).getHiveEntities();

        List<HookNotification> notifications = alterTable.getNotificationMessages();

        AssertJUnit.assertNull("Notifications should be null when entities are null", notifications);
    }

    @Test
    public void testGetNotificationMessages_MetastoreHook_MultipleEntities() throws Exception {
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getMetastoreEvent()).thenReturn(alterTableEvent);
        when(alterTableEvent.getOldTable()).thenReturn(mock(org.apache.hadoop.hive.metastore.api.Table.class));
        when(alterTableEvent.getNewTable()).thenReturn(mock(org.apache.hadoop.hive.metastore.api.Table.class));

        AlterTable alterTable = spy(new AlterTable(context));
        
        // Mock multiple entities
        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        
        AtlasEntity tableEntity = new AtlasEntity("hive_table");
        tableEntity.setAttribute("qualifiedName", "default.altered_table@cluster");
        entities.addEntity(tableEntity);
        
        AtlasEntity columnEntity = new AtlasEntity("hive_column");
        columnEntity.setAttribute("qualifiedName", "default.altered_table.new_col@cluster");
        entities.addEntity(columnEntity);
        
        AtlasEntity processEntity = new AtlasEntity("hive_process");
        processEntity.setAttribute("qualifiedName", "alter_table_process@cluster");
        entities.addEntity(processEntity);
        
        doReturn(entities).when(alterTable).getHiveMetastoreEntities();
        doReturn("system_user").when(alterTable).getUserName();

        List<HookNotification> notifications = alterTable.getNotificationMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        AssertJUnit.assertEquals("Should have exactly one notification", 1, notifications.size());
        
        EntityUpdateRequestV2 updateRequest = (EntityUpdateRequestV2) notifications.get(0);
        AssertJUnit.assertEquals("Should have three entities", 3, 
                updateRequest.getEntities().getEntities().size());
    }

    // ========== INHERITED METHOD TESTS ==========

    @Test
    public void testInheritedMethod_GetHiveMetastoreEntities() throws Exception {
        // Test that AlterTable properly inherits and can call parent methods
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getMetastoreEvent()).thenReturn(alterTableEvent);
        when(context.getHiveOperation()).thenReturn(HiveOperation.ALTERTABLE_ADDCOLS);
        
        org.apache.hadoop.hive.metastore.api.Table oldTable = mock(org.apache.hadoop.hive.metastore.api.Table.class);
        org.apache.hadoop.hive.metastore.api.Table newTable = mock(org.apache.hadoop.hive.metastore.api.Table.class);
        
        when(alterTableEvent.getOldTable()).thenReturn(oldTable);
        when(alterTableEvent.getNewTable()).thenReturn(newTable);
        when(oldTable.getDbName()).thenReturn("default");
        when(oldTable.getTableName()).thenReturn("test_table");
        when(newTable.getDbName()).thenReturn("default");
        when(newTable.getTableName()).thenReturn("test_table");

        AlterTable alterTable = spy(new AlterTable(context));
        
        // Mock the processTable method to avoid complex setup
        doNothing().when(alterTable).processTable(any(), any());

        AtlasEntitiesWithExtInfo result = alterTable.getHiveMetastoreEntities();
        AssertJUnit.assertNotNull("Should return entities from inherited method", result);
    }

    @Test
    public void testInheritedMethod_GetHiveEntities() throws Exception {
        // Test inherited getHiveEntities method
        when(context.isMetastoreHook()).thenReturn(false);
        when(context.getOutputs()).thenReturn(createMockOutputs());

        AlterTable alterTable = spy(new AlterTable(context));
        
        // Mock inherited method dependencies
        Hive mockHive = mock(Hive.class);
        Table mockTable = mock(Table.class);
        when(mockTable.getDbName()).thenReturn("default");
        when(mockTable.getTableName()).thenReturn("altered_table");
        when(mockHive.getTable("default", "altered_table")).thenReturn(mockTable);
        doReturn(mockHive).when(alterTable).getHive();
        doNothing().when(alterTable).processTable(any(), any());

        AtlasEntitiesWithExtInfo result = alterTable.getHiveEntities();
        AssertJUnit.assertNotNull("Should return entities from inherited method", result);
    }

    @Test
    public void testInheritedMethod_GetUserName() throws Exception {
        when(context.getUserName()).thenReturn("alter_user");

        AlterTable alterTable = new AlterTable(context);
        String userName = alterTable.getUserName();
        
        AssertJUnit.assertEquals("Should return correct username from inherited method", 
                "alter_user", userName);
        verify(context).getUserName();
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    public void testGetNotificationMessages_ExceptionInGetHiveMetastoreEntities() throws Exception {
        when(context.isMetastoreHook()).thenReturn(true);

        AlterTable alterTable = spy(new AlterTable(context));
        doThrow(new RuntimeException("Metastore error")).when(alterTable).getHiveMetastoreEntities();

        try {
            alterTable.getNotificationMessages();
            AssertJUnit.fail("Should have thrown exception");
        } catch (RuntimeException e) {
            AssertJUnit.assertEquals("Exception message should match", "Metastore error", e.getMessage());
        }
    }

    @Test
    public void testGetNotificationMessages_ExceptionInGetHiveEntities() throws Exception {
        when(context.isMetastoreHook()).thenReturn(false);

        AlterTable alterTable = spy(new AlterTable(context));
        doThrow(new NoSuchObjectException("Table not found")).when(alterTable).getHiveEntities();

        try {
            alterTable.getNotificationMessages();
            AssertJUnit.fail("Should have thrown exception");
        } catch (NoSuchObjectException e) {
            AssertJUnit.assertEquals("Exception message should match", "Table not found", e.getMessage());
        }
    }

    @Test
    public void testGetNotificationMessages_ExceptionInGetUserName() throws Exception {
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getMetastoreEvent()).thenReturn(alterTableEvent);

        AlterTable alterTable = spy(new AlterTable(context));
        
        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        entities.addEntity(new AtlasEntity("hive_table"));
        doReturn(entities).when(alterTable).getHiveMetastoreEntities();
        doThrow(new RuntimeException("User error")).when(alterTable).getUserName();

        try {
            alterTable.getNotificationMessages();
            AssertJUnit.fail("Should have thrown exception");
        } catch (RuntimeException e) {
            AssertJUnit.assertEquals("Exception message should match", "User error", e.getMessage());
        }
    }

    // ========== INTEGRATION TESTS ==========

    @Test
    public void testGetNotificationMessages_FullWorkflow_AlterTableAddColumn() throws Exception {
        // Simulate ALTER TABLE ADD COLUMN scenario
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getMetastoreEvent()).thenReturn(alterTableEvent);
        when(context.getHiveOperation()).thenReturn(HiveOperation.ALTERTABLE_ADDCOLS);
        when(context.getHiveConf()).thenReturn(hiveConf);
        when(context.getHostName()).thenReturn("test-host");

        // Mock old and new table structures
        org.apache.hadoop.hive.metastore.api.Table oldTable = createMockMetastoreTable("test_table", 2);
        org.apache.hadoop.hive.metastore.api.Table newTable = createMockMetastoreTable("test_table", 3); // Added column
        
        when(alterTableEvent.getOldTable()).thenReturn(oldTable);
        when(alterTableEvent.getNewTable()).thenReturn(newTable);

        AlterTable alterTable = spy(new AlterTable(context));
        
        // Mock timing methods
        doReturn("ALTER TABLE test_table ADD COLUMN new_col STRING").when(alterTable).getQueryString();
        doReturn(System.currentTimeMillis() - 1000).when(alterTable).getQueryStartTime();
        doReturn("admin").when(alterTable).getUserName();
        doReturn("alter_query_123").when(alterTable).getQueryId();

        // Mock entity creation
        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        AtlasEntity tableEntity = new AtlasEntity("hive_table");
        tableEntity.setAttribute("qualifiedName", "default.test_table@cluster");
        tableEntity.setAttribute("name", "test_table");
        entities.addEntity(tableEntity);
        
        AtlasEntity newColumnEntity = new AtlasEntity("hive_column");
        newColumnEntity.setAttribute("qualifiedName", "default.test_table.new_col@cluster");
        newColumnEntity.setAttribute("name", "new_col");
        entities.addEntity(newColumnEntity);
        
        doReturn(entities).when(alterTable).getHiveMetastoreEntities();

        List<HookNotification> notifications = alterTable.getNotificationMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        AssertJUnit.assertEquals("Should have one notification", 1, notifications.size());
        
        EntityUpdateRequestV2 updateRequest = (EntityUpdateRequestV2) notifications.get(0);
        AssertJUnit.assertEquals("User should be admin", "admin", updateRequest.getUser());
        AssertJUnit.assertEquals("Should have 2 entities (table + new column)", 2, 
                updateRequest.getEntities().getEntities().size());
    }

    @Test
    public void testGetNotificationMessages_FullWorkflow_AlterTableRename() throws Exception {
        // Simulate ALTER TABLE RENAME scenario
        when(context.isMetastoreHook()).thenReturn(false);
        when(context.getHiveOperation()).thenReturn(HiveOperation.ALTERTABLE_RENAME);
        when(context.getOutputs()).thenReturn(createMockRenamedTableOutputs());

        AlterTable alterTable = spy(new AlterTable(context));
        
        // Mock dependencies for non-metastore hook
        Hive mockHive = mock(Hive.class);
        Table renamedTable = mock(Table.class);
        when(renamedTable.getDbName()).thenReturn("default");
        when(renamedTable.getTableName()).thenReturn("renamed_table");
        when(renamedTable.getTableType()).thenReturn(MANAGED_TABLE);
        when(mockHive.getTable("default", "renamed_table")).thenReturn(renamedTable);
        doReturn(mockHive).when(alterTable).getHive();
        
        // Mock timing and user methods
        doReturn("ALTER TABLE old_table RENAME TO renamed_table").when(alterTable).getQueryString();
        doReturn(System.currentTimeMillis() - 2000).when(alterTable).getQueryStartTime();
        doReturn("hive_admin").when(alterTable).getUserName();
        doReturn("rename_query_456").when(alterTable).getQueryId();

        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        AtlasEntity renamedTableEntity = new AtlasEntity("hive_table");
        renamedTableEntity.setAttribute("qualifiedName", "default.renamed_table@cluster");
        renamedTableEntity.setAttribute("name", "renamed_table");
        entities.addEntity(renamedTableEntity);
        
        doReturn(entities).when(alterTable).getHiveEntities();

        List<HookNotification> notifications = alterTable.getNotificationMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        AssertJUnit.assertEquals("Should have one notification", 1, notifications.size());
        
        EntityUpdateRequestV2 updateRequest = (EntityUpdateRequestV2) notifications.get(0);
        AssertJUnit.assertEquals("User should be hive_admin", "hive_admin", updateRequest.getUser());
        AssertJUnit.assertEquals("Should have 1 entity (renamed table)", 1, 
                updateRequest.getEntities().getEntities().size());
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    public void testGetNotificationMessages_BothMetastoreAndHiveEntitiesNull() throws Exception {
        when(context.isMetastoreHook()).thenReturn(true);

        AlterTable alterTable = spy(new AlterTable(context));
        doReturn(null).when(alterTable).getHiveMetastoreEntities();

        List<HookNotification> notifications = alterTable.getNotificationMessages();
        AssertJUnit.assertNull("Should return null when both entity methods return null", notifications);

        // Test the other path
        when(context.isMetastoreHook()).thenReturn(false);
        doReturn(null).when(alterTable).getHiveEntities();

        notifications = alterTable.getNotificationMessages();
        AssertJUnit.assertNull("Should return null when both entity methods return null", notifications);
    }

    @Test
    public void testGetNotificationMessages_EmptyUserName() throws Exception {
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getMetastoreEvent()).thenReturn(alterTableEvent);

        AlterTable alterTable = spy(new AlterTable(context));
        
        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        entities.addEntity(new AtlasEntity("hive_table"));
        doReturn(entities).when(alterTable).getHiveMetastoreEntities();
        doReturn("").when(alterTable).getUserName(); // Empty username

        List<HookNotification> notifications = alterTable.getNotificationMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        EntityUpdateRequestV2 updateRequest = (EntityUpdateRequestV2) notifications.get(0);
        AssertJUnit.assertEquals("User should be empty string", "", updateRequest.getUser());
    }

    @Test
    public void testGetNotificationMessages_NullUserName() throws Exception {
        when(context.isMetastoreHook()).thenReturn(false);
        when(context.getOutputs()).thenReturn(createMockOutputs());

        AlterTable alterTable = spy(new AlterTable(context));
        
        AtlasEntitiesWithExtInfo entities = new AtlasEntitiesWithExtInfo();
        entities.addEntity(new AtlasEntity("hive_table"));
        doReturn(entities).when(alterTable).getHiveEntities();
        doReturn(null).when(alterTable).getUserName(); // Null username

        List<HookNotification> notifications = alterTable.getNotificationMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        EntityUpdateRequestV2 updateRequest = (EntityUpdateRequestV2) notifications.get(0);
        AssertJUnit.assertNull("User should be null", updateRequest.getUser());
    }

    // ========== HELPER METHODS ==========

    private Set<Entity> createMockOutputs() {
        Entity entity = mock(Entity.class);
        Table qlTable = mock(Table.class);
        when(entity.getType()).thenReturn(Entity.Type.TABLE);
        when(entity.getTable()).thenReturn(qlTable);
        when(qlTable.getDbName()).thenReturn("default");
        when(qlTable.getTableName()).thenReturn("altered_table");
        return Collections.singleton(entity);
    }

    private Set<Entity> createMockRenamedTableOutputs() {
        Entity entity = mock(Entity.class);
        Table qlTable = mock(Table.class);
        when(entity.getType()).thenReturn(Entity.Type.TABLE);
        when(entity.getTable()).thenReturn(qlTable);
        when(qlTable.getDbName()).thenReturn("default");
        when(qlTable.getTableName()).thenReturn("renamed_table");
        return Collections.singleton(entity);
    }

    private org.apache.hadoop.hive.metastore.api.Table createMockMetastoreTable(String tableName, int columnCount) {
        org.apache.hadoop.hive.metastore.api.Table table = mock(org.apache.hadoop.hive.metastore.api.Table.class);
        when(table.getDbName()).thenReturn("default");
        when(table.getTableName()).thenReturn(tableName);
        when(table.getTableType()).thenReturn(MANAGED_TABLE.toString());
        when(table.getOwner()).thenReturn("hive");
        
        StorageDescriptor sd = mock(StorageDescriptor.class);
        List<FieldSchema> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            FieldSchema col = new FieldSchema("col" + i, "string", null);
            columns.add(col);
        }
        when(sd.getCols()).thenReturn(columns);
        when(sd.getLocation()).thenReturn("/warehouse/default/" + tableName);
        when(sd.getInputFormat()).thenReturn("org.apache.hadoop.mapred.TextInputFormat");
        when(sd.getOutputFormat()).thenReturn("org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat");
        when(sd.isCompressed()).thenReturn(false);
        when(sd.getNumBuckets()).thenReturn(-1);
        
        SerDeInfo serdeInfo = mock(SerDeInfo.class);
        when(serdeInfo.getSerializationLib()).thenReturn("org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe");
        when(serdeInfo.getParameters()).thenReturn(new HashMap<String, String>());
        when(sd.getSerdeInfo()).thenReturn(serdeInfo);
        
        when(table.getSd()).thenReturn(sd);
        when(table.getPartitionKeys()).thenReturn(new ArrayList<FieldSchema>());
        when(table.getParameters()).thenReturn(new HashMap<String, String>());
        
        return table;
    }
}