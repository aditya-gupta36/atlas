package org.apache.atlas.hive.hook.events;

import org.apache.atlas.hive.hook.AtlasHiveHookContext;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntitiesWithExtInfo;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntityExtInfo;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntityWithExtInfo;
import org.apache.atlas.model.instance.AtlasObjectId;
import org.apache.atlas.model.notification.HookNotification;
import org.apache.atlas.model.notification.HookNotification.EntityPartialUpdateRequestV2;
import org.apache.atlas.model.notification.HookNotification.EntityUpdateRequestV2;
import org.apache.atlas.model.notification.HookNotification.EntityCreateRequestV2;
import org.apache.hadoop.hive.metastore.IHMSHandler;
import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.SerDeInfo;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.events.AlterTableEvent;
import org.apache.hadoop.hive.ql.hooks.Entity;
import org.apache.hadoop.hive.ql.hooks.ReadEntity;
import org.apache.hadoop.hive.ql.hooks.WriteEntity;
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
import static org.apache.hadoop.hive.metastore.TableType.MANAGED_TABLE;
import static org.mockito.Mockito.*;

public class AlterTableRenameTest {

    @Mock
    AtlasHiveHookContext context;

    @Mock
    AlterTableEvent alterTableEvent;

    @Mock
    Table oldTable;

    @Mock
    Table newTable;

    @Mock
    Hive hive;

    @Mock
    HiveConf hiveConf;

    @Mock
    SessionState sessionState;

    @Captor
    ArgumentCaptor<List<HookNotification>> notificationsCaptor;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ========== CONSTRUCTOR TESTS ==========

    @Test
    public void testConstructor() {
        AlterTableRename alterTableRename = new AlterTableRename(context);
        AssertJUnit.assertNotNull("AlterTableRename should be instantiated", alterTableRename);
        AssertJUnit.assertTrue("AlterTableRename should extend BaseHiveEvent", 
                alterTableRename instanceof BaseHiveEvent);
    }

    @Test
    public void testConstructorWithNullContext() {
        AlterTableRename alterTableRename = new AlterTableRename(null);
        AssertJUnit.assertNotNull("AlterTableRename should be instantiated even with null context", 
                alterTableRename);
    }

    // ========== getNotificationMessages() TESTS ==========

    @Test
    public void testGetNotificationMessages_MetastoreHook() throws Exception {
        when(context.isMetastoreHook()).thenReturn(true);
        
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        List<HookNotification> expectedNotifications = Arrays.asList(
                mock(EntityPartialUpdateRequestV2.class),
                mock(EntityUpdateRequestV2.class)
        );
        doReturn(expectedNotifications).when(alterTableRename).getHiveMetastoreMessages();

        List<HookNotification> notifications = alterTableRename.getNotificationMessages();

        AssertJUnit.assertEquals("Should return metastore messages", expectedNotifications, notifications);
        verify(alterTableRename).getHiveMetastoreMessages();
        verify(alterTableRename, never()).getHiveMessages();
    }

    @Test
    public void testGetNotificationMessages_NonMetastoreHook() throws Exception {
        when(context.isMetastoreHook()).thenReturn(false);
        
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        List<HookNotification> expectedNotifications = Arrays.asList(
                mock(EntityPartialUpdateRequestV2.class),
                mock(EntityUpdateRequestV2.class),
                mock(EntityCreateRequestV2.class)
        );
        doReturn(expectedNotifications).when(alterTableRename).getHiveMessages();

        List<HookNotification> notifications = alterTableRename.getNotificationMessages();

        AssertJUnit.assertEquals("Should return hive messages", expectedNotifications, notifications);
        verify(alterTableRename).getHiveMessages();
        verify(alterTableRename, never()).getHiveMetastoreMessages();
    }

    @Test
    public void testGetNotificationMessages_ExceptionHandling() throws Exception {
        when(context.isMetastoreHook()).thenReturn(true);
        
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doThrow(new RuntimeException("Test exception")).when(alterTableRename).getHiveMetastoreMessages();

        try {
            alterTableRename.getNotificationMessages();
            AssertJUnit.fail("Should have thrown exception");
        } catch (RuntimeException e) {
            AssertJUnit.assertEquals("Exception message should match", "Test exception", e.getMessage());
        }
    }

    // ========== getHiveMetastoreMessages() TESTS ==========

    @Test
    public void testGetHiveMetastoreMessages_Success() throws Exception {
        // Setup metastore event
        when(context.getMetastoreEvent()).thenReturn(alterTableEvent);
        
        org.apache.hadoop.hive.metastore.api.Table oldMetastoreTable = createMockMetastoreTable("old_table");
        org.apache.hadoop.hive.metastore.api.Table newMetastoreTable = createMockMetastoreTable("new_table");
        
        when(alterTableEvent.getOldTable()).thenReturn(oldMetastoreTable);
        when(alterTableEvent.getNewTable()).thenReturn(newMetastoreTable);

        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        // Mock toTable method
        doReturn(oldTable).when(alterTableRename).toTable(oldMetastoreTable);
        doReturn(newTable).when(alterTableRename).toTable(newMetastoreTable);
        
        // Mock processTables
        doNothing().when(alterTableRename).processTables(eq(oldTable), eq(newTable), any(List.class));

        List<HookNotification> notifications = alterTableRename.getHiveMetastoreMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        verify(alterTableRename).processTables(eq(oldTable), eq(newTable), any(List.class));
    }

    @Test
    public void testGetHiveMetastoreMessages_NewTableNull() throws Exception {
        when(context.getMetastoreEvent()).thenReturn(alterTableEvent);
        
        org.apache.hadoop.hive.metastore.api.Table oldMetastoreTable = createMockMetastoreTable("old_table");
        
        when(alterTableEvent.getOldTable()).thenReturn(oldMetastoreTable);
        when(alterTableEvent.getNewTable()).thenReturn(createMockMetastoreTable("new_table"));

        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn(oldTable).when(alterTableRename).toTable(oldMetastoreTable);
        doReturn(null).when(alterTableRename).toTable(any()); // Return null for new table

        List<HookNotification> notifications = alterTableRename.getHiveMetastoreMessages();

        AssertJUnit.assertTrue("Should return empty list when new table is null", 
                notifications.isEmpty());
        verify(alterTableRename, never()).processTables(any(), any(), any());
    }

    @Test
    public void testGetHiveMetastoreMessages_ExceptionInToTable() throws Exception {
        when(context.getMetastoreEvent()).thenReturn(alterTableEvent);
        
        org.apache.hadoop.hive.metastore.api.Table oldMetastoreTable = createMockMetastoreTable("old_table");
        when(alterTableEvent.getOldTable()).thenReturn(oldMetastoreTable);
        when(alterTableEvent.getNewTable()).thenReturn(createMockMetastoreTable("new_table"));

        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doThrow(new RuntimeException("toTable failed")).when(alterTableRename).toTable(any());

        try {
            alterTableRename.getHiveMetastoreMessages();
            AssertJUnit.fail("Should have thrown exception");
        } catch (RuntimeException e) {
            AssertJUnit.assertEquals("Exception message should match", "toTable failed", e.getMessage());
        }
    }

    // ========== getHiveMessages() TESTS ==========

    @Test
    public void testGetHiveMessages_Success() throws Exception {
        // Setup inputs
        ReadEntity inputEntity = mock(ReadEntity.class);
        when(inputEntity.getTable()).thenReturn(oldTable);
        Set<ReadEntity> inputs = Collections.singleton(inputEntity);
        
        // Setup outputs
        WriteEntity outputEntity = mock(WriteEntity.class);
        when(outputEntity.getType()).thenReturn(Entity.Type.TABLE);
        when(outputEntity.getTable()).thenReturn(newTable);
        Set<WriteEntity> outputs = Collections.singleton(outputEntity);

        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn(inputs).when(alterTableRename).getInputs();
        doReturn(outputs).when(alterTableRename).getOutputs();
        
        // Mock table properties
        when(oldTable.getDbName()).thenReturn("default");
        when(oldTable.getTableName()).thenReturn("old_table");
        when(newTable.getDbName()).thenReturn("default");
        when(newTable.getTableName()).thenReturn("new_table");
        
        // Mock Hive
        doReturn(hive).when(alterTableRename).getHive();
        when(hive.getTable("default", "new_table")).thenReturn(newTable);
        
        // Mock processTables
        doNothing().when(alterTableRename).processTables(eq(oldTable), eq(newTable), any(List.class));

        List<HookNotification> notifications = alterTableRename.getHiveMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        verify(alterTableRename).processTables(eq(oldTable), eq(newTable), any(List.class));
    }

    @Test
    public void testGetHiveMessages_EmptyInputs() throws Exception {
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn(Collections.emptySet()).when(alterTableRename).getInputs();

        List<HookNotification> notifications = alterTableRename.getHiveMessages();

        AssertJUnit.assertTrue("Should return empty list when inputs are empty", notifications.isEmpty());
        verify(alterTableRename, never()).processTables(any(), any(), any());
    }

    @Test
    public void testGetHiveMessages_NullInputs() throws Exception {
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn(null).when(alterTableRename).getInputs();

        List<HookNotification> notifications = alterTableRename.getHiveMessages();

        AssertJUnit.assertTrue("Should return empty list when inputs are null", notifications.isEmpty());
        verify(alterTableRename, never()).processTables(any(), any(), any());
    }

    @Test
    public void testGetHiveMessages_SameTableNameInOutput() throws Exception {
        // Setup inputs
        ReadEntity inputEntity = mock(ReadEntity.class);
        when(inputEntity.getTable()).thenReturn(oldTable);
        Set<ReadEntity> inputs = Collections.singleton(inputEntity);
        
        // Setup outputs with same table name (should be skipped)
        WriteEntity sameTableOutput = mock(WriteEntity.class);
        WriteEntity differentTableOutput = mock(WriteEntity.class);
        when(sameTableOutput.getType()).thenReturn(Entity.Type.TABLE);
        when(sameTableOutput.getTable()).thenReturn(oldTable); // Same table
        when(differentTableOutput.getType()).thenReturn(Entity.Type.TABLE);
        when(differentTableOutput.getTable()).thenReturn(newTable); // Different table
        
        Set<WriteEntity> outputs = new LinkedHashSet<>();
        outputs.add(sameTableOutput);
        outputs.add(differentTableOutput);

        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn(inputs).when(alterTableRename).getInputs();
        doReturn(outputs).when(alterTableRename).getOutputs();
        
        // Mock table properties
        when(oldTable.getDbName()).thenReturn("default");
        when(oldTable.getTableName()).thenReturn("old_table");
        when(newTable.getDbName()).thenReturn("default");
        when(newTable.getTableName()).thenReturn("new_table");
        
        // Mock Hive
        doReturn(hive).when(alterTableRename).getHive();
        when(hive.getTable("default", "new_table")).thenReturn(newTable);
        
        // Mock processTables
        doNothing().when(alterTableRename).processTables(eq(oldTable), eq(newTable), any(List.class));

        List<HookNotification> notifications = alterTableRename.getHiveMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        verify(alterTableRename).processTables(eq(oldTable), eq(newTable), any(List.class));
    }

    @Test
    public void testGetHiveMessages_NoTableInOutputs() throws Exception {
        // Setup inputs
        ReadEntity inputEntity = mock(ReadEntity.class);
        when(inputEntity.getTable()).thenReturn(oldTable);
        Set<ReadEntity> inputs = Collections.singleton(inputEntity);
        
        // Setup outputs with non-table entity
        WriteEntity outputEntity = mock(WriteEntity.class);
        when(outputEntity.getType()).thenReturn(Entity.Type.PARTITION);
        Set<WriteEntity> outputs = Collections.singleton(outputEntity);

        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn(inputs).when(alterTableRename).getInputs();
        doReturn(outputs).when(alterTableRename).getOutputs();

        List<HookNotification> notifications = alterTableRename.getHiveMessages();

        AssertJUnit.assertTrue("Should return empty list when no table in outputs", notifications.isEmpty());
        verify(alterTableRename, never()).processTables(any(), any(), any());
    }

    @Test
    public void testGetHiveMessages_HiveGetTableThrowsException() throws Exception {
        // Setup inputs
        ReadEntity inputEntity = mock(ReadEntity.class);
        when(inputEntity.getTable()).thenReturn(oldTable);
        Set<ReadEntity> inputs = Collections.singleton(inputEntity);
        
        // Setup outputs
        WriteEntity outputEntity = mock(WriteEntity.class);
        when(outputEntity.getType()).thenReturn(Entity.Type.TABLE);
        when(outputEntity.getTable()).thenReturn(newTable);
        Set<WriteEntity> outputs = Collections.singleton(outputEntity);

        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn(inputs).when(alterTableRename).getInputs();
        doReturn(outputs).when(alterTableRename).getOutputs();
        
        // Mock table properties
        when(oldTable.getDbName()).thenReturn("default");
        when(oldTable.getTableName()).thenReturn("old_table");
        when(newTable.getDbName()).thenReturn("default");
        when(newTable.getTableName()).thenReturn("new_table");
        
        // Mock Hive to throw exception
        doReturn(hive).when(alterTableRename).getHive();
        when(hive.getTable("default", "new_table")).thenThrow(new NoSuchObjectException("Table not found"));

        try {
            alterTableRename.getHiveMessages();
            AssertJUnit.fail("Should have thrown exception");
        } catch (NoSuchObjectException e) {
            AssertJUnit.assertEquals("Exception message should match", "Table not found", e.getMessage());
        }
    }

    // ========== processTables() TESTS ==========

    @Test
    public void testProcessTables_Success() throws Exception {
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        // Mock toTableEntity
        AtlasEntityWithExtInfo oldTableEntity = createMockTableEntity("default.old_table@cluster");
        AtlasEntityWithExtInfo newTableEntity = createMockTableEntity("default.new_table@cluster");
        doReturn(oldTableEntity).when(alterTableRename).toTableEntity(oldTable);
        doReturn(newTableEntity).when(alterTableRename).toTableEntity(newTable);
        
        // Mock other dependencies
        doReturn("test_user").when(alterTableRename).getUserName();
        when(oldTable.getTableName()).thenReturn("old_table");
        
        // Mock sub-methods
        doNothing().when(alterTableRename).renameColumns(any(), any(), any(), any());
        doNothing().when(alterTableRename).renameStorageDesc(any(), any(), any());
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.processTables(oldTable, newTable, notifications);

        AssertJUnit.assertFalse("Notifications should not be empty", notifications.isEmpty());
        AssertJUnit.assertTrue("Should contain partial update", 
                notifications.stream().anyMatch(n -> n instanceof EntityPartialUpdateRequestV2));
        AssertJUnit.assertTrue("Should contain full update", 
                notifications.stream().anyMatch(n -> n instanceof EntityUpdateRequestV2));
        
        verify(alterTableRename, times(2)).renameColumns(any(), any(), any(), any());
        verify(alterTableRename).renameStorageDesc(any(), any(), any());
    }

    @Test
    public void testProcessTables_OldTableEntityNull() throws Exception {
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        doReturn(null).when(alterTableRename).toTableEntity(oldTable);
        doReturn(createMockTableEntity("default.new_table@cluster")).when(alterTableRename).toTableEntity(newTable);
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.processTables(oldTable, newTable, notifications);

        AssertJUnit.assertTrue("Notifications should be empty when old table entity is null", 
                notifications.isEmpty());
        verify(alterTableRename, never()).renameColumns(any(), any(), any(), any());
    }

    @Test
    public void testProcessTables_NewTableEntityNull() throws Exception {
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        doReturn(createMockTableEntity("default.old_table@cluster")).when(alterTableRename).toTableEntity(oldTable);
        doReturn(null).when(alterTableRename).toTableEntity(newTable);
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.processTables(oldTable, newTable, notifications);

        AssertJUnit.assertTrue("Notifications should be empty when new table entity is null", 
                notifications.isEmpty());
        verify(alterTableRename, never()).renameColumns(any(), any(), any(), any());
    }

    @Test
    public void testProcessTables_WithDDLEntity_NonMetastore() throws Exception {
        when(context.isMetastoreHook()).thenReturn(false);
        
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        AtlasEntityWithExtInfo oldTableEntity = createMockTableEntity("default.old_table@cluster");
        AtlasEntityWithExtInfo newTableEntity = createMockTableEntity("default.new_table@cluster");
        doReturn(oldTableEntity).when(alterTableRename).toTableEntity(oldTable);
        doReturn(newTableEntity).when(alterTableRename).toTableEntity(newTable);
        
        doReturn("test_user").when(alterTableRename).getUserName();
        when(oldTable.getTableName()).thenReturn("old_table");
        
        // Mock DDL entity creation
        AtlasEntity ddlEntity = new AtlasEntity("hive_ddl");
        doReturn(ddlEntity).when(alterTableRename).createHiveDDLEntity(any(AtlasEntity.class), eq(true));
        
        // Mock sub-methods
        doNothing().when(alterTableRename).renameColumns(any(), any(), any(), any());
        doNothing().when(alterTableRename).renameStorageDesc(any(), any(), any());
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.processTables(oldTable, newTable, notifications);

        AssertJUnit.assertFalse("Notifications should not be empty", notifications.isEmpty());
        AssertJUnit.assertTrue("Should contain DDL create request", 
                notifications.stream().anyMatch(n -> n instanceof EntityCreateRequestV2));
        
        verify(alterTableRename).createHiveDDLEntity(any(AtlasEntity.class), eq(true));
    }

    @Test
    public void testProcessTables_WithNullDDLEntity_NonMetastore() throws Exception {
        when(context.isMetastoreHook()).thenReturn(false);
        
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        AtlasEntityWithExtInfo oldTableEntity = createMockTableEntity("default.old_table@cluster");
        AtlasEntityWithExtInfo newTableEntity = createMockTableEntity("default.new_table@cluster");
        doReturn(oldTableEntity).when(alterTableRename).toTableEntity(oldTable);
        doReturn(newTableEntity).when(alterTableRename).toTableEntity(newTable);
        
        doReturn("test_user").when(alterTableRename).getUserName();
        when(oldTable.getTableName()).thenReturn("old_table");
        
        // Mock DDL entity creation to return null
        doReturn(null).when(alterTableRename).createHiveDDLEntity(any(AtlasEntity.class), eq(true));
        
        // Mock sub-methods
        doNothing().when(alterTableRename).renameColumns(any(), any(), any(), any());
        doNothing().when(alterTableRename).renameStorageDesc(any(), any(), any());
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.processTables(oldTable, newTable, notifications);

        AssertJUnit.assertFalse("Notifications should not be empty", notifications.isEmpty());
        // Should not contain DDL create request when DDL entity is null
        AssertJUnit.assertFalse("Should not contain DDL create request when DDL entity is null", 
                notifications.stream().anyMatch(n -> n instanceof EntityCreateRequestV2));
    }

    // ========== renameColumns() TESTS ==========

    @Test
    public void testRenameColumns_Success() throws Exception {
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn("test_user").when(alterTableRename).getUserName();
        doReturn("default.new_table.col1@cluster").when(alterTableRename)
                .getColumnQualifiedName("default.new_table@cluster", "col1");
        
        // Create mock column
        AtlasEntity columnEntity = new AtlasEntity("hive_column");
        columnEntity.setGuid("col1_guid");
        columnEntity.setAttribute("qualifiedName", "default.old_table.col1@cluster");
        columnEntity.setAttribute("name", "col1");
        
        AtlasObjectId columnId = new AtlasObjectId("hive_column", "qualifiedName", "default.old_table.col1@cluster");
        columnId.setGuid("col1_guid");
        
        AtlasEntityExtInfo oldEntityExtInfo = mock(AtlasEntityExtInfo.class);
        when(oldEntityExtInfo.getEntity("col1_guid")).thenReturn(columnEntity);
        
        List<AtlasObjectId> columns = Arrays.asList(columnId);
        List<HookNotification> notifications = new ArrayList<>();
        
        alterTableRename.renameColumns(columns, oldEntityExtInfo, "default.new_table@cluster", notifications);
        
        AssertJUnit.assertEquals("Should have one notification", 1, notifications.size());
        AssertJUnit.assertTrue("Should be partial update request", 
                notifications.get(0) instanceof EntityPartialUpdateRequestV2);
    }

    @Test
    public void testRenameColumns_EmptyColumns() throws Exception {
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.renameColumns(Collections.emptyList(), mock(AtlasEntityExtInfo.class), 
                "default.new_table@cluster", notifications);
        
        AssertJUnit.assertTrue("Should have no notifications for empty columns", notifications.isEmpty());
    }

    @Test
    public void testRenameColumns_NullColumns() throws Exception {
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.renameColumns(null, mock(AtlasEntityExtInfo.class), 
                "default.new_table@cluster", notifications);
        
        AssertJUnit.assertTrue("Should have no notifications for null columns", notifications.isEmpty());
    }

    // ========== renameStorageDesc() TESTS ==========

    @Test
    public void testRenameStorageDesc_Success() throws Exception {
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn("test_user").when(alterTableRename).getUserName();
        
        // Create old storage descriptor
        AtlasEntity oldSd = new AtlasEntity("hive_storagedesc");
        oldSd.setAttribute("qualifiedName", "default.old_table@cluster_storage");
        oldSd.setAttribute("table", new AtlasObjectId("hive_table", "qualifiedName", "default.old_table@cluster"));
        
        // Create new storage descriptor
        AtlasEntity newSd = new AtlasEntity("hive_storagedesc");
        newSd.setAttribute("qualifiedName", "default.new_table@cluster_storage");
        newSd.setAttribute("table", new AtlasObjectId("hive_table", "qualifiedName", "default.new_table@cluster"));
        
        AtlasEntityWithExtInfo oldEntityExtInfo = mock(AtlasEntityWithExtInfo.class);
        AtlasEntityWithExtInfo newEntityExtInfo = mock(AtlasEntityWithExtInfo.class);
        
        doReturn(oldSd).when(alterTableRename).getStorageDescEntity(oldEntityExtInfo);
        doReturn(newSd).when(alterTableRename).getStorageDescEntity(newEntityExtInfo);
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.renameStorageDesc(oldEntityExtInfo, newEntityExtInfo, notifications);
        
        AssertJUnit.assertEquals("Should have one notification", 1, notifications.size());
        AssertJUnit.assertTrue("Should be partial update request", 
                notifications.get(0) instanceof EntityPartialUpdateRequestV2);
    }

    @Test
    public void testRenameStorageDesc_OldSdNull() throws Exception {
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        AtlasEntityWithExtInfo oldEntityExtInfo = mock(AtlasEntityWithExtInfo.class);
        AtlasEntityWithExtInfo newEntityExtInfo = mock(AtlasEntityWithExtInfo.class);
        
        doReturn(null).when(alterTableRename).getStorageDescEntity(oldEntityExtInfo);
        doReturn(new AtlasEntity("hive_storagedesc")).when(alterTableRename).getStorageDescEntity(newEntityExtInfo);
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.renameStorageDesc(oldEntityExtInfo, newEntityExtInfo, notifications);
        
        AssertJUnit.assertTrue("Should have no notifications when old SD is null", notifications.isEmpty());
    }

    @Test
    public void testRenameStorageDesc_NewSdNull() throws Exception {
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        AtlasEntityWithExtInfo oldEntityExtInfo = mock(AtlasEntityWithExtInfo.class);
        AtlasEntityWithExtInfo newEntityExtInfo = mock(AtlasEntityWithExtInfo.class);
        
        doReturn(new AtlasEntity("hive_storagedesc")).when(alterTableRename).getStorageDescEntity(oldEntityExtInfo);
        doReturn(null).when(alterTableRename).getStorageDescEntity(newEntityExtInfo);
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.renameStorageDesc(oldEntityExtInfo, newEntityExtInfo, notifications);
        
        AssertJUnit.assertTrue("Should have no notifications when new SD is null", notifications.isEmpty());
    }

    // ========== getStorageDescEntity() TESTS ==========

    @Test
    public void testGetStorageDescEntity_Success() throws Exception {
        AlterTableRename alterTableRename = new AlterTableRename(context);
        
        AtlasEntity tableEntity = new AtlasEntity("hive_table");
        AtlasObjectId sdId = new AtlasObjectId("hive_storagedesc", "qualifiedName", "sd_qualified_name");
        sdId.setGuid("sd_guid");
        tableEntity.setRelationshipAttribute("sd", sdId);
        
        AtlasEntity sdEntity = new AtlasEntity("hive_storagedesc");
        
        AtlasEntityWithExtInfo tableEntityWithExtInfo = new AtlasEntityWithExtInfo(tableEntity);
        tableEntityWithExtInfo.addReferredEntity("sd_guid", sdEntity);
        
        AtlasEntity result = alterTableRename.getStorageDescEntity(tableEntityWithExtInfo);
        
        AssertJUnit.assertEquals("Should return the storage descriptor entity", sdEntity, result);
    }

    @Test
    public void testGetStorageDescEntity_NullTableEntity() throws Exception {
        AlterTableRename alterTableRename = new AlterTableRename(context);
        
        AtlasEntity result = alterTableRename.getStorageDescEntity(null);
        
        AssertJUnit.assertNull("Should return null for null table entity", result);
    }

    @Test
    public void testGetStorageDescEntity_NullEntity() throws Exception {
        AlterTableRename alterTableRename = new AlterTableRename(context);
        
        AtlasEntityWithExtInfo tableEntityWithExtInfo = new AtlasEntityWithExtInfo();
        
        AtlasEntity result = alterTableRename.getStorageDescEntity(tableEntityWithExtInfo);
        
        AssertJUnit.assertNull("Should return null when entity is null", result);
    }

    @Test
    public void testGetStorageDescEntity_NoStorageDescAttribute() throws Exception {
        AlterTableRename alterTableRename = new AlterTableRename(context);
        
        AtlasEntity tableEntity = new AtlasEntity("hive_table");
        // No storage descriptor attribute set
        
        AtlasEntityWithExtInfo tableEntityWithExtInfo = new AtlasEntityWithExtInfo(tableEntity);
        
        AtlasEntity result = alterTableRename.getStorageDescEntity(tableEntityWithExtInfo);
        
        AssertJUnit.assertNull("Should return null when no storage descriptor attribute", result);
    }

    @Test
    public void testGetStorageDescEntity_NonAtlasObjectIdAttribute() throws Exception {
        AlterTableRename alterTableRename = new AlterTableRename(context);
        
        AtlasEntity tableEntity = new AtlasEntity("hive_table");
        tableEntity.setRelationshipAttribute("sd", "not_an_atlas_object_id"); // Invalid type
        
        AtlasEntityWithExtInfo tableEntityWithExtInfo = new AtlasEntityWithExtInfo(tableEntity);
        
        AtlasEntity result = alterTableRename.getStorageDescEntity(tableEntityWithExtInfo);
        
        AssertJUnit.assertNull("Should return null when attribute is not AtlasObjectId", result);
    }

    // ========== INTEGRATION TESTS ==========

    @Test
    public void testFullWorkflow_MetastoreHook() throws Exception {
        // Setup metastore event
        when(context.isMetastoreHook()).thenReturn(true);
        when(context.getMetastoreEvent()).thenReturn(alterTableEvent);
        
        org.apache.hadoop.hive.metastore.api.Table oldMetastoreTable = createMockMetastoreTable("old_table");
        org.apache.hadoop.hive.metastore.api.Table newMetastoreTable = createMockMetastoreTable("new_table");
        
        when(alterTableEvent.getOldTable()).thenReturn(oldMetastoreTable);
        when(alterTableEvent.getNewTable()).thenReturn(newMetastoreTable);

        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        // Mock table entities
        AtlasEntityWithExtInfo oldTableEntity = createMockTableEntity("default.old_table@cluster");
        AtlasEntityWithExtInfo newTableEntity = createMockTableEntity("default.new_table@cluster");
        
        doReturn(oldTable).when(alterTableRename).toTable(oldMetastoreTable);
        doReturn(newTable).when(alterTableRename).toTable(newMetastoreTable);
        doReturn(oldTableEntity).when(alterTableRename).toTableEntity(oldTable);
        doReturn(newTableEntity).when(alterTableRename).toTableEntity(newTable);
        doReturn("admin").when(alterTableRename).getUserName();
        doReturn("default.new_table.col@cluster").when(alterTableRename)
                .getColumnQualifiedName(anyString(), anyString());
        
        when(oldTable.getTableName()).thenReturn("old_table");
        
        // Mock sub-methods
        doNothing().when(alterTableRename).renameColumns(any(), any(), any(), any());
        doNothing().when(alterTableRename).renameStorageDesc(any(), any(), any());

        List<HookNotification> notifications = alterTableRename.getNotificationMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        AssertJUnit.assertFalse("Notifications should not be empty", notifications.isEmpty());
        
        // Should contain both partial and full updates
        AssertJUnit.assertTrue("Should contain partial update", 
                notifications.stream().anyMatch(n -> n instanceof EntityPartialUpdateRequestV2));
        AssertJUnit.assertTrue("Should contain full update", 
                notifications.stream().anyMatch(n -> n instanceof EntityUpdateRequestV2));
    }

    @Test
    public void testFullWorkflow_NonMetastoreHook() throws Exception {
        // Setup non-metastore context
        when(context.isMetastoreHook()).thenReturn(false);
        
        // Setup inputs and outputs
        ReadEntity inputEntity = mock(ReadEntity.class);
        when(inputEntity.getTable()).thenReturn(oldTable);
        Set<ReadEntity> inputs = Collections.singleton(inputEntity);
        
        WriteEntity outputEntity = mock(WriteEntity.class);
        when(outputEntity.getType()).thenReturn(Entity.Type.TABLE);
        when(outputEntity.getTable()).thenReturn(newTable);
        Set<WriteEntity> outputs = Collections.singleton(outputEntity);

        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn(inputs).when(alterTableRename).getInputs();
        doReturn(outputs).when(alterTableRename).getOutputs();
        doReturn(hive).when(alterTableRename).getHive();
        
        // Mock table properties
        when(oldTable.getDbName()).thenReturn("default");
        when(oldTable.getTableName()).thenReturn("old_table");
        when(newTable.getDbName()).thenReturn("default");
        when(newTable.getTableName()).thenReturn("new_table");
        when(hive.getTable("default", "new_table")).thenReturn(newTable);
        
        // Mock table entities
        AtlasEntityWithExtInfo oldTableEntity = createMockTableEntity("default.old_table@cluster");
        AtlasEntityWithExtInfo newTableEntity = createMockTableEntity("default.new_table@cluster");
        
        doReturn(oldTableEntity).when(alterTableRename).toTableEntity(oldTable);
        doReturn(newTableEntity).when(alterTableRename).toTableEntity(newTable);
        doReturn("admin").when(alterTableRename).getUserName();
        doReturn("default.new_table.col@cluster").when(alterTableRename)
                .getColumnQualifiedName(anyString(), anyString());
        
        // Mock DDL entity
        AtlasEntity ddlEntity = new AtlasEntity("hive_ddl");
        doReturn(ddlEntity).when(alterTableRename).createHiveDDLEntity(any(AtlasEntity.class), eq(true));
        
        // Mock sub-methods
        doNothing().when(alterTableRename).renameColumns(any(), any(), any(), any());
        doNothing().when(alterTableRename).renameStorageDesc(any(), any(), any());

        List<HookNotification> notifications = alterTableRename.getNotificationMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        AssertJUnit.assertFalse("Notifications should not be empty", notifications.isEmpty());
        
        // Should contain partial update, full update, and DDL create
        AssertJUnit.assertTrue("Should contain partial update", 
                notifications.stream().anyMatch(n -> n instanceof EntityPartialUpdateRequestV2));
        AssertJUnit.assertTrue("Should contain full update", 
                notifications.stream().anyMatch(n -> n instanceof EntityUpdateRequestV2));
        AssertJUnit.assertTrue("Should contain DDL create", 
                notifications.stream().anyMatch(n -> n instanceof EntityCreateRequestV2));
    }

    // ========== HELPER METHODS ==========

    private org.apache.hadoop.hive.metastore.api.Table createMockMetastoreTable(String tableName) {
        org.apache.hadoop.hive.metastore.api.Table table = mock(org.apache.hadoop.hive.metastore.api.Table.class);
        when(table.getDbName()).thenReturn("default");
        when(table.getTableName()).thenReturn(tableName);
        when(table.getTableType()).thenReturn(MANAGED_TABLE.toString());
        when(table.getOwner()).thenReturn("hive");
        
        StorageDescriptor sd = mock(StorageDescriptor.class);
        when(sd.getCols()).thenReturn(Arrays.asList(new FieldSchema("col1", "string", null)));
        when(sd.getLocation()).thenReturn("/warehouse/default/" + tableName);
        when(table.getSd()).thenReturn(sd);
        when(table.getPartitionKeys()).thenReturn(new ArrayList<>());
        when(table.getParameters()).thenReturn(new HashMap<>());
        
        return table;
    }

    private AtlasEntityWithExtInfo createMockTableEntity(String qualifiedName) {
        AtlasEntity tableEntity = new AtlasEntity("hive_table");
        tableEntity.setAttribute("qualifiedName", qualifiedName);
        tableEntity.setAttribute("name", qualifiedName.split("\\.")[1].split("@")[0]);
        
        // Add relationship attributes
        AtlasObjectId columnId = new AtlasObjectId("hive_column", "qualifiedName", qualifiedName + ".col1");
        columnId.setGuid("col1_guid");
        tableEntity.setRelationshipAttribute("columns", Arrays.asList(columnId));
        tableEntity.setRelationshipAttribute("partitionKeys", new ArrayList<>());
        
        AtlasObjectId sdId = new AtlasObjectId("hive_storagedesc", "qualifiedName", qualifiedName + "_storage");
        sdId.setGuid("sd_guid");
        tableEntity.setRelationshipAttribute("sd", sdId);
        
        AtlasEntityWithExtInfo entityWithExtInfo = new AtlasEntityWithExtInfo(tableEntity);
        
        // Add referred entities
        AtlasEntity columnEntity = new AtlasEntity("hive_column");
        columnEntity.setAttribute("qualifiedName", qualifiedName + ".col1");
        columnEntity.setAttribute("name", "col1");
        entityWithExtInfo.addReferredEntity("col1_guid", columnEntity);
        
        AtlasEntity sdEntity = new AtlasEntity("hive_storagedesc");
        sdEntity.setAttribute("qualifiedName", qualifiedName + "_storage");
        entityWithExtInfo.addReferredEntity("sd_guid", sdEntity);
        
        return entityWithExtInfo;
    }
}