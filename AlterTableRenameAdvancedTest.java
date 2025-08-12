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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.apache.hadoop.hive.metastore.TableType.EXTERNAL_TABLE;
import static org.apache.hadoop.hive.metastore.TableType.MANAGED_TABLE;
import static org.mockito.Mockito.*;

public class AlterTableRenameAdvancedTest {

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

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    public void testGetHiveMessages_MultipleTableOutputsWithSameName() throws Exception {
        // Test case where Hive sends multiple outputs with same table name
        ReadEntity inputEntity = mock(ReadEntity.class);
        when(inputEntity.getTable()).thenReturn(oldTable);
        Set<ReadEntity> inputs = Collections.singleton(inputEntity);
        
        // Create multiple outputs with same and different names
        WriteEntity sameOutput1 = mock(WriteEntity.class);
        WriteEntity sameOutput2 = mock(WriteEntity.class);
        WriteEntity differentOutput = mock(WriteEntity.class);
        
        when(sameOutput1.getType()).thenReturn(Entity.Type.TABLE);
        when(sameOutput1.getTable()).thenReturn(oldTable);
        when(sameOutput2.getType()).thenReturn(Entity.Type.TABLE);
        when(sameOutput2.getTable()).thenReturn(oldTable);
        when(differentOutput.getType()).thenReturn(Entity.Type.TABLE);
        when(differentOutput.getTable()).thenReturn(newTable);
        
        Set<WriteEntity> outputs = new LinkedHashSet<>();
        outputs.add(sameOutput1);
        outputs.add(sameOutput2);
        outputs.add(differentOutput);

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
        
        // Mock processTables
        doNothing().when(alterTableRename).processTables(eq(oldTable), eq(newTable), any(List.class));

        List<HookNotification> notifications = alterTableRename.getHiveMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        verify(alterTableRename).processTables(eq(oldTable), eq(newTable), any(List.class));
        verify(hive).getTable("default", "new_table");
    }

    @Test
    public void testGetHiveMessages_CrossDatabaseRename() throws Exception {
        // Test renaming table across different databases
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
        
        // Different databases
        when(oldTable.getDbName()).thenReturn("old_db");
        when(oldTable.getTableName()).thenReturn("table_name");
        when(newTable.getDbName()).thenReturn("new_db");
        when(newTable.getTableName()).thenReturn("table_name");
        when(hive.getTable("new_db", "table_name")).thenReturn(newTable);
        
        doNothing().when(alterTableRename).processTables(eq(oldTable), eq(newTable), any(List.class));

        List<HookNotification> notifications = alterTableRename.getHiveMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        verify(alterTableRename).processTables(eq(oldTable), eq(newTable), any(List.class));
    }

    @Test
    public void testGetHiveMessages_CaseSensitiveTableNames() throws Exception {
        // Test case sensitivity in table name comparison
        ReadEntity inputEntity = mock(ReadEntity.class);
        when(inputEntity.getTable()).thenReturn(oldTable);
        Set<ReadEntity> inputs = Collections.singleton(inputEntity);
        
        WriteEntity sameTableOutput = mock(WriteEntity.class);
        WriteEntity differentTableOutput = mock(WriteEntity.class);
        when(sameTableOutput.getType()).thenReturn(Entity.Type.TABLE);
        when(sameTableOutput.getTable()).thenReturn(oldTable);
        when(differentTableOutput.getType()).thenReturn(Entity.Type.TABLE);
        when(differentTableOutput.getTable()).thenReturn(newTable);
        
        Set<WriteEntity> outputs = new LinkedHashSet<>();
        outputs.add(sameTableOutput);
        outputs.add(differentTableOutput);

        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn(inputs).when(alterTableRename).getInputs();
        doReturn(outputs).when(alterTableRename).getOutputs();
        doReturn(hive).when(alterTableRename).getHive();
        
        // Test case sensitivity - same name but different case
        when(oldTable.getDbName()).thenReturn("default");
        when(oldTable.getTableName()).thenReturn("OLD_TABLE");
        when(newTable.getDbName()).thenReturn("default");
        when(newTable.getTableName()).thenReturn("old_table"); // Different case
        when(hive.getTable("default", "old_table")).thenReturn(newTable);
        
        doNothing().when(alterTableRename).processTables(eq(oldTable), eq(newTable), any(List.class));

        List<HookNotification> notifications = alterTableRename.getHiveMessages();

        AssertJUnit.assertNotNull("Notifications should not be null", notifications);
        verify(alterTableRename).processTables(eq(oldTable), eq(newTable), any(List.class));
    }

    @Test
    public void testProcessTables_LargeNumberOfColumns() throws Exception {
        // Test performance with many columns
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        AtlasEntityWithExtInfo oldTableEntity = createTableEntityWithManyColumns("default.old_table@cluster", 100);
        AtlasEntityWithExtInfo newTableEntity = createTableEntityWithManyColumns("default.new_table@cluster", 100);
        
        doReturn(oldTableEntity).when(alterTableRename).toTableEntity(oldTable);
        doReturn(newTableEntity).when(alterTableRename).toTableEntity(newTable);
        doReturn("test_user").when(alterTableRename).getUserName();
        when(oldTable.getTableName()).thenReturn("old_table");
        
        // Mock getColumnQualifiedName for all columns
        for (int i = 0; i < 100; i++) {
            doReturn("default.new_table.col" + i + "@cluster").when(alterTableRename)
                    .getColumnQualifiedName("default.new_table@cluster", "col" + i);
        }
        
        // Mock sub-methods
        doNothing().when(alterTableRename).renameStorageDesc(any(), any(), any());
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.processTables(oldTable, newTable, notifications);

        AssertJUnit.assertFalse("Notifications should not be empty", notifications.isEmpty());
        // Should have partial update, full update, and 100 column updates (for columns and partitionKeys)
        long partialUpdates = notifications.stream()
                .filter(n -> n instanceof EntityPartialUpdateRequestV2)
                .count();
        AssertJUnit.assertTrue("Should have many partial updates for columns", partialUpdates > 100);
    }

    @Test
    public void testProcessTables_WithPartitionKeys() throws Exception {
        // Test renaming with partition keys
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        AtlasEntityWithExtInfo oldTableEntity = createTableEntityWithPartitions("default.old_table@cluster");
        AtlasEntityWithExtInfo newTableEntity = createTableEntityWithPartitions("default.new_table@cluster");
        
        doReturn(oldTableEntity).when(alterTableRename).toTableEntity(oldTable);
        doReturn(newTableEntity).when(alterTableRename).toTableEntity(newTable);
        doReturn("test_user").when(alterTableRename).getUserName();
        when(oldTable.getTableName()).thenReturn("old_table");
        
        doReturn("default.new_table.year@cluster").when(alterTableRename)
                .getColumnQualifiedName("default.new_table@cluster", "year");
        doReturn("default.new_table.month@cluster").when(alterTableRename)
                .getColumnQualifiedName("default.new_table@cluster", "month");
        doReturn("default.new_table.col1@cluster").when(alterTableRename)
                .getColumnQualifiedName("default.new_table@cluster", "col1");
        
        doNothing().when(alterTableRename).renameStorageDesc(any(), any(), any());
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.processTables(oldTable, newTable, notifications);

        AssertJUnit.assertFalse("Notifications should not be empty", notifications.isEmpty());
        
        // Verify renameColumns was called for both columns and partition keys
        verify(alterTableRename, times(2)).renameColumns(any(), any(), any(), any());
    }

    @Test
    public void testProcessTables_SetAliasWithPreviousName() throws Exception {
        // Test that alias is properly set to old table name
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        AtlasEntityWithExtInfo oldTableEntity = createMockTableEntity("default.old_table@cluster");
        AtlasEntityWithExtInfo newTableEntity = createMockTableEntity("default.new_table@cluster");
        
        doReturn(oldTableEntity).when(alterTableRename).toTableEntity(oldTable);
        doReturn(newTableEntity).when(alterTableRename).toTableEntity(newTable);
        doReturn("test_user").when(alterTableRename).getUserName();
        when(oldTable.getTableName()).thenReturn("old_table_name");
        
        doNothing().when(alterTableRename).renameColumns(any(), any(), any(), any());
        doNothing().when(alterTableRename).renameStorageDesc(any(), any(), any());
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.processTables(oldTable, newTable, notifications);

        // Verify alias was set
        @SuppressWarnings("unchecked")
        List<String> aliases = (List<String>) newTableEntity.getEntity().getAttribute("aliases");
        AssertJUnit.assertNotNull("Aliases should be set", aliases);
        AssertJUnit.assertEquals("Should have one alias", 1, aliases.size());
        AssertJUnit.assertEquals("Alias should be old table name", "old_table_name", aliases.get(0));
    }

    @Test
    public void testProcessTables_RemoveKnownTable() throws Exception {
        // Test that old table is removed from known tables
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        AtlasEntityWithExtInfo oldTableEntity = createMockTableEntity("default.old_table@cluster");
        AtlasEntityWithExtInfo newTableEntity = createMockTableEntity("default.new_table@cluster");
        
        doReturn(oldTableEntity).when(alterTableRename).toTableEntity(oldTable);
        doReturn(newTableEntity).when(alterTableRename).toTableEntity(newTable);
        doReturn("test_user").when(alterTableRename).getUserName();
        when(oldTable.getTableName()).thenReturn("old_table");
        
        doNothing().when(alterTableRename).renameColumns(any(), any(), any(), any());
        doNothing().when(alterTableRename).renameStorageDesc(any(), any(), any());
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.processTables(oldTable, newTable, notifications);

        verify(context).removeFromKnownTable("default.old_table@cluster");
    }

    @Test
    public void testRenameColumns_MultipleColumnsWithDifferentTypes() throws Exception {
        // Test renaming columns with different data types
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn("test_user").when(alterTableRename).getUserName();
        
        // Create columns with different types
        AtlasEntity stringColumn = new AtlasEntity("hive_column");
        stringColumn.setGuid("string_col_guid");
        stringColumn.setAttribute("qualifiedName", "default.old_table.string_col@cluster");
        stringColumn.setAttribute("name", "string_col");
        stringColumn.setAttribute("type", "string");
        
        AtlasEntity intColumn = new AtlasEntity("hive_column");
        intColumn.setGuid("int_col_guid");
        intColumn.setAttribute("qualifiedName", "default.old_table.int_col@cluster");
        intColumn.setAttribute("name", "int_col");
        intColumn.setAttribute("type", "int");
        
        AtlasObjectId stringColumnId = new AtlasObjectId("hive_column", "qualifiedName", "default.old_table.string_col@cluster");
        stringColumnId.setGuid("string_col_guid");
        AtlasObjectId intColumnId = new AtlasObjectId("hive_column", "qualifiedName", "default.old_table.int_col@cluster");
        intColumnId.setGuid("int_col_guid");
        
        AtlasEntityExtInfo oldEntityExtInfo = mock(AtlasEntityExtInfo.class);
        when(oldEntityExtInfo.getEntity("string_col_guid")).thenReturn(stringColumn);
        when(oldEntityExtInfo.getEntity("int_col_guid")).thenReturn(intColumn);
        
        doReturn("default.new_table.string_col@cluster").when(alterTableRename)
                .getColumnQualifiedName("default.new_table@cluster", "string_col");
        doReturn("default.new_table.int_col@cluster").when(alterTableRename)
                .getColumnQualifiedName("default.new_table@cluster", "int_col");
        
        List<AtlasObjectId> columns = Arrays.asList(stringColumnId, intColumnId);
        List<HookNotification> notifications = new ArrayList<>();
        
        alterTableRename.renameColumns(columns, oldEntityExtInfo, "default.new_table@cluster", notifications);
        
        AssertJUnit.assertEquals("Should have two notifications", 2, notifications.size());
        AssertJUnit.assertTrue("Both should be partial update requests", 
                notifications.stream().allMatch(n -> n instanceof EntityPartialUpdateRequestV2));
    }

    @Test
    public void testRenameStorageDesc_RemoveTableAttribute() throws Exception {
        // Test that table attribute is removed from storage descriptor during rename
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn("test_user").when(alterTableRename).getUserName();
        
        AtlasEntity oldSd = new AtlasEntity("hive_storagedesc");
        oldSd.setAttribute("qualifiedName", "default.old_table@cluster_storage");
        oldSd.setAttribute("table", new AtlasObjectId("hive_table", "qualifiedName", "default.old_table@cluster"));
        
        AtlasEntity newSd = new AtlasEntity("hive_storagedesc");
        newSd.setAttribute("qualifiedName", "default.new_table@cluster_storage");
        newSd.setAttribute("table", new AtlasObjectId("hive_table", "qualifiedName", "default.new_table@cluster"));
        newSd.setRelationshipAttribute("columns", Arrays.asList(new AtlasObjectId("hive_column", "guid", "col_guid")));
        
        AtlasEntityWithExtInfo oldEntityExtInfo = mock(AtlasEntityWithExtInfo.class);
        AtlasEntityWithExtInfo newEntityExtInfo = mock(AtlasEntityWithExtInfo.class);
        
        doReturn(oldSd).when(alterTableRename).getStorageDescEntity(oldEntityExtInfo);
        doReturn(newSd).when(alterTableRename).getStorageDescEntity(newEntityExtInfo);
        
        List<HookNotification> notifications = new ArrayList<>();
        alterTableRename.renameStorageDesc(oldEntityExtInfo, newEntityExtInfo, notifications);
        
        AssertJUnit.assertEquals("Should have one notification", 1, notifications.size());
        
        EntityPartialUpdateRequestV2 updateRequest = (EntityPartialUpdateRequestV2) notifications.get(0);
        AtlasEntity updatedSd = updateRequest.getEntityInfo().getEntity();
        
        // Verify table attribute was removed
        AssertJUnit.assertNull("Table attribute should be removed", updatedSd.getAttribute("table"));
        // Verify relationship attributes were set to null
        AssertJUnit.assertNull("Relationship attributes should be null", updatedSd.getRelationshipAttributes());
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    public void testGetHiveMetastoreMessages_NullAlterTableEvent() throws Exception {
        when(context.getMetastoreEvent()).thenReturn(null);
        
        AlterTableRename alterTableRename = new AlterTableRename(context);
        
        try {
            alterTableRename.getHiveMetastoreMessages();
            AssertJUnit.fail("Should have thrown exception for null event");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testGetHiveMessages_GetHiveThrowsException() throws Exception {
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
        
        when(oldTable.getDbName()).thenReturn("default");
        when(oldTable.getTableName()).thenReturn("old_table");
        when(newTable.getDbName()).thenReturn("default");
        when(newTable.getTableName()).thenReturn("new_table");
        
        doThrow(new RuntimeException("Hive connection failed")).when(alterTableRename).getHive();

        try {
            alterTableRename.getHiveMessages();
            AssertJUnit.fail("Should have thrown exception");
        } catch (RuntimeException e) {
            AssertJUnit.assertEquals("Exception message should match", "Hive connection failed", e.getMessage());
        }
    }

    @Test
    public void testProcessTables_ToTableEntityThrowsException() throws Exception {
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        doThrow(new RuntimeException("Failed to create table entity")).when(alterTableRename).toTableEntity(oldTable);
        
        List<HookNotification> notifications = new ArrayList<>();
        
        try {
            alterTableRename.processTables(oldTable, newTable, notifications);
            AssertJUnit.fail("Should have thrown exception");
        } catch (RuntimeException e) {
            AssertJUnit.assertEquals("Exception message should match", "Failed to create table entity", e.getMessage());
        }
    }

    @Test
    public void testRenameColumns_GetColumnQualifiedNameThrowsException() throws Exception {
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn("test_user").when(alterTableRename).getUserName();
        
        AtlasEntity columnEntity = new AtlasEntity("hive_column");
        columnEntity.setGuid("col1_guid");
        columnEntity.setAttribute("qualifiedName", "default.old_table.col1@cluster");
        columnEntity.setAttribute("name", "col1");
        
        AtlasObjectId columnId = new AtlasObjectId("hive_column", "qualifiedName", "default.old_table.col1@cluster");
        columnId.setGuid("col1_guid");
        
        AtlasEntityExtInfo oldEntityExtInfo = mock(AtlasEntityExtInfo.class);
        when(oldEntityExtInfo.getEntity("col1_guid")).thenReturn(columnEntity);
        
        doThrow(new RuntimeException("Failed to get column qualified name"))
                .when(alterTableRename).getColumnQualifiedName("default.new_table@cluster", "col1");
        
        List<AtlasObjectId> columns = Arrays.asList(columnId);
        List<HookNotification> notifications = new ArrayList<>();
        
        try {
            alterTableRename.renameColumns(columns, oldEntityExtInfo, "default.new_table@cluster", notifications);
            AssertJUnit.fail("Should have thrown exception");
        } catch (RuntimeException e) {
            AssertJUnit.assertEquals("Exception message should match", "Failed to get column qualified name", e.getMessage());
        }
    }

    // ========== CONCURRENCY TESTS ==========

    @Test
    public void testConcurrentAccess() throws Exception {
        // Test concurrent access to renameColumns method
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        doReturn("test_user").when(alterTableRename).getUserName();
        
        AtlasEntity columnEntity = new AtlasEntity("hive_column");
        columnEntity.setGuid("col1_guid");
        columnEntity.setAttribute("qualifiedName", "default.old_table.col1@cluster");
        columnEntity.setAttribute("name", "col1");
        
        AtlasObjectId columnId = new AtlasObjectId("hive_column", "qualifiedName", "default.old_table.col1@cluster");
        columnId.setGuid("col1_guid");
        
        AtlasEntityExtInfo oldEntityExtInfo = mock(AtlasEntityExtInfo.class);
        when(oldEntityExtInfo.getEntity("col1_guid")).thenReturn(columnEntity);
        
        doReturn("default.new_table.col1@cluster").when(alterTableRename)
                .getColumnQualifiedName("default.new_table@cluster", "col1");
        
        List<AtlasObjectId> columns = Arrays.asList(columnId);
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    List<HookNotification> notifications = new ArrayList<>();
                    alterTableRename.renameColumns(columns, oldEntityExtInfo, "default.new_table@cluster", notifications);
                    AssertJUnit.assertEquals("Should have one notification", 1, notifications.size());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            futures.add(future);
        }
        
        // Wait for all futures to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        
        // All operations should complete successfully
        AssertJUnit.assertTrue("All concurrent operations should succeed", 
                futures.stream().allMatch(f -> f.isDone() && !f.isCompletedExceptionally()));
    }

    @Test
    public void testMemoryUsageWithLargeDatasets() throws Exception {
        // Test memory usage with large number of notifications
        AlterTableRename alterTableRename = spy(new AlterTableRename(context));
        
        AtlasEntityWithExtInfo oldTableEntity = createTableEntityWithManyColumns("default.old_table@cluster", 1000);
        AtlasEntityWithExtInfo newTableEntity = createTableEntityWithManyColumns("default.new_table@cluster", 1000);
        
        doReturn(oldTableEntity).when(alterTableRename).toTableEntity(oldTable);
        doReturn(newTableEntity).when(alterTableRename).toTableEntity(newTable);
        doReturn("test_user").when(alterTableRename).getUserName();
        when(oldTable.getTableName()).thenReturn("old_table");
        
        // Mock getColumnQualifiedName for all columns
        for (int i = 0; i < 1000; i++) {
            doReturn("default.new_table.col" + i + "@cluster").when(alterTableRename)
                    .getColumnQualifiedName("default.new_table@cluster", "col" + i);
        }
        
        doNothing().when(alterTableRename).renameStorageDesc(any(), any(), any());
        
        List<HookNotification> notifications = new ArrayList<>();
        
        long beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        alterTableRename.processTables(oldTable, newTable, notifications);
        long afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        AssertJUnit.assertFalse("Notifications should not be empty", notifications.isEmpty());
        AssertJUnit.assertTrue("Should handle large datasets without excessive memory usage", 
                (afterMemory - beforeMemory) < 50 * 1024 * 1024); // Less than 50MB increase
    }

    // ========== HELPER METHODS ==========

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

    private AtlasEntityWithExtInfo createTableEntityWithManyColumns(String qualifiedName, int columnCount) {
        AtlasEntity tableEntity = new AtlasEntity("hive_table");
        tableEntity.setAttribute("qualifiedName", qualifiedName);
        tableEntity.setAttribute("name", qualifiedName.split("\\.")[1].split("@")[0]);
        
        List<AtlasObjectId> columnIds = new ArrayList<>();
        AtlasEntityWithExtInfo entityWithExtInfo = new AtlasEntityWithExtInfo(tableEntity);
        
        for (int i = 0; i < columnCount; i++) {
            AtlasObjectId columnId = new AtlasObjectId("hive_column", "qualifiedName", qualifiedName + ".col" + i);
            columnId.setGuid("col" + i + "_guid");
            columnIds.add(columnId);
            
            AtlasEntity columnEntity = new AtlasEntity("hive_column");
            columnEntity.setAttribute("qualifiedName", qualifiedName + ".col" + i);
            columnEntity.setAttribute("name", "col" + i);
            entityWithExtInfo.addReferredEntity("col" + i + "_guid", columnEntity);
        }
        
        tableEntity.setRelationshipAttribute("columns", columnIds);
        tableEntity.setRelationshipAttribute("partitionKeys", new ArrayList<>());
        
        AtlasObjectId sdId = new AtlasObjectId("hive_storagedesc", "qualifiedName", qualifiedName + "_storage");
        sdId.setGuid("sd_guid");
        tableEntity.setRelationshipAttribute("sd", sdId);
        
        AtlasEntity sdEntity = new AtlasEntity("hive_storagedesc");
        sdEntity.setAttribute("qualifiedName", qualifiedName + "_storage");
        entityWithExtInfo.addReferredEntity("sd_guid", sdEntity);
        
        return entityWithExtInfo;
    }

    private AtlasEntityWithExtInfo createTableEntityWithPartitions(String qualifiedName) {
        AtlasEntity tableEntity = new AtlasEntity("hive_table");
        tableEntity.setAttribute("qualifiedName", qualifiedName);
        tableEntity.setAttribute("name", qualifiedName.split("\\.")[1].split("@")[0]);
        
        // Add columns
        AtlasObjectId columnId = new AtlasObjectId("hive_column", "qualifiedName", qualifiedName + ".col1");
        columnId.setGuid("col1_guid");
        tableEntity.setRelationshipAttribute("columns", Arrays.asList(columnId));
        
        // Add partition keys
        AtlasObjectId partKey1 = new AtlasObjectId("hive_column", "qualifiedName", qualifiedName + ".year");
        partKey1.setGuid("year_guid");
        AtlasObjectId partKey2 = new AtlasObjectId("hive_column", "qualifiedName", qualifiedName + ".month");
        partKey2.setGuid("month_guid");
        tableEntity.setRelationshipAttribute("partitionKeys", Arrays.asList(partKey1, partKey2));
        
        AtlasObjectId sdId = new AtlasObjectId("hive_storagedesc", "qualifiedName", qualifiedName + "_storage");
        sdId.setGuid("sd_guid");
        tableEntity.setRelationshipAttribute("sd", sdId);
        
        AtlasEntityWithExtInfo entityWithExtInfo = new AtlasEntityWithExtInfo(tableEntity);
        
        // Add referred entities
        AtlasEntity columnEntity = new AtlasEntity("hive_column");
        columnEntity.setAttribute("qualifiedName", qualifiedName + ".col1");
        columnEntity.setAttribute("name", "col1");
        entityWithExtInfo.addReferredEntity("col1_guid", columnEntity);
        
        AtlasEntity yearEntity = new AtlasEntity("hive_column");
        yearEntity.setAttribute("qualifiedName", qualifiedName + ".year");
        yearEntity.setAttribute("name", "year");
        entityWithExtInfo.addReferredEntity("year_guid", yearEntity);
        
        AtlasEntity monthEntity = new AtlasEntity("hive_column");
        monthEntity.setAttribute("qualifiedName", qualifiedName + ".month");
        monthEntity.setAttribute("name", "month");
        entityWithExtInfo.addReferredEntity("month_guid", monthEntity);
        
        AtlasEntity sdEntity = new AtlasEntity("hive_storagedesc");
        sdEntity.setAttribute("qualifiedName", qualifiedName + "_storage");
        entityWithExtInfo.addReferredEntity("sd_guid", sdEntity);
        
        return entityWithExtInfo;
    }
}