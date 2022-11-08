package com.isoft.rfid.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.isoft.rfid.IntegrationTest;
import com.isoft.rfid.domain.Tasks;
import com.isoft.rfid.repository.TasksRepository;
import com.isoft.rfid.repository.search.TasksSearchRepository;
import com.isoft.rfid.service.dto.TasksDTO;
import com.isoft.rfid.service.mapper.TasksMapper;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.apache.commons.collections4.IterableUtils;
import org.assertj.core.util.IterableUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link TasksResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class TasksResourceIT {

    private static final String DEFAULT_NAME_EN = "AAAAAAAAAA";
    private static final String UPDATED_NAME_EN = "BBBBBBBBBB";

    private static final String DEFAULT_NAME_AR = "AAAAAAAAAA";
    private static final String UPDATED_NAME_AR = "BBBBBBBBBB";

    private static final Integer DEFAULT_STATUS = 1;
    private static final Integer UPDATED_STATUS = 2;

    private static final String DEFAULT_CODE = "AAAAAAAAAA";
    private static final String UPDATED_CODE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/tasks";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";
    private static final String ENTITY_SEARCH_API_URL = "/api/_search/tasks";

    private static Random random = new Random();
    private static AtomicLong count = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private TasksRepository tasksRepository;

    @Autowired
    private TasksMapper tasksMapper;

    @Autowired
    private TasksSearchRepository tasksSearchRepository;

    @Autowired
    private MockMvc restTasksMockMvc;

    private Tasks tasks;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Tasks createEntity() {
        Tasks tasks = new Tasks().nameEn(DEFAULT_NAME_EN).nameAr(DEFAULT_NAME_AR).status(DEFAULT_STATUS).code(DEFAULT_CODE);
        return tasks;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Tasks createUpdatedEntity() {
        Tasks tasks = new Tasks().nameEn(UPDATED_NAME_EN).nameAr(UPDATED_NAME_AR).status(UPDATED_STATUS).code(UPDATED_CODE);
        return tasks;
    }

    @AfterEach
    public void cleanupElasticSearchRepository() {
        tasksSearchRepository.deleteAll();
        assertThat(tasksSearchRepository.count()).isEqualTo(0);
    }

    @BeforeEach
    public void initTest() {
        tasks = createEntity();
    }

    @Test
    void createTasks() throws Exception {
        int databaseSizeBeforeCreate = tasksRepository.findAll().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        // Create the Tasks
        TasksDTO tasksDTO = tasksMapper.toDto(tasks);
        restTasksMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(tasksDTO)))
            .andExpect(status().isCreated());

        // Validate the Tasks in the database
        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeCreate + 1);
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(tasksSearchRepository.findAll());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore + 1);
            });
        Tasks testTasks = tasksList.get(tasksList.size() - 1);
        assertThat(testTasks.getNameEn()).isEqualTo(DEFAULT_NAME_EN);
        assertThat(testTasks.getNameAr()).isEqualTo(DEFAULT_NAME_AR);
        assertThat(testTasks.getStatus()).isEqualTo(DEFAULT_STATUS);
        assertThat(testTasks.getCode()).isEqualTo(DEFAULT_CODE);
    }

    @Test
    void createTasksWithExistingId() throws Exception {
        // Create the Tasks with an existing ID
        tasks.setId(1L);
        TasksDTO tasksDTO = tasksMapper.toDto(tasks);

        int databaseSizeBeforeCreate = tasksRepository.findAll().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tasksSearchRepository.findAll());

        // An entity with an existing ID cannot be created, so this API call must fail
        restTasksMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(tasksDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Tasks in the database
        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeCreate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void checkNameEnIsRequired() throws Exception {
        int databaseSizeBeforeTest = tasksRepository.findAll().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        // set the field null
        tasks.setNameEn(null);

        // Create the Tasks, which fails.
        TasksDTO tasksDTO = tasksMapper.toDto(tasks);

        restTasksMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(tasksDTO)))
            .andExpect(status().isBadRequest());

        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeTest);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void checkNameArIsRequired() throws Exception {
        int databaseSizeBeforeTest = tasksRepository.findAll().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        // set the field null
        tasks.setNameAr(null);

        // Create the Tasks, which fails.
        TasksDTO tasksDTO = tasksMapper.toDto(tasks);

        restTasksMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(tasksDTO)))
            .andExpect(status().isBadRequest());

        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeTest);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void getAllTasks() throws Exception {
        // Initialize the database
        tasksRepository.save(tasks);

        // Get all the tasksList
        restTasksMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].nameEn").value(hasItem(DEFAULT_NAME_EN)))
            .andExpect(jsonPath("$.[*].nameAr").value(hasItem(DEFAULT_NAME_AR)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS)))
            .andExpect(jsonPath("$.[*].code").value(hasItem(DEFAULT_CODE)));
    }

    @Test
    void getTasks() throws Exception {
        // Initialize the database
        tasksRepository.save(tasks);

        // Get the tasks
        restTasksMockMvc
            .perform(get(ENTITY_API_URL_ID, tasks.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.nameEn").value(DEFAULT_NAME_EN))
            .andExpect(jsonPath("$.nameAr").value(DEFAULT_NAME_AR))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS))
            .andExpect(jsonPath("$.code").value(DEFAULT_CODE));
    }

    @Test
    void getNonExistingTasks() throws Exception {
        // Get the tasks
        restTasksMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingTasks() throws Exception {
        // Initialize the database
        tasksRepository.save(tasks);

        int databaseSizeBeforeUpdate = tasksRepository.findAll().size();
        tasksSearchRepository.save(tasks);
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tasksSearchRepository.findAll());

        // Update the tasks
        Tasks updatedTasks = tasksRepository.findById(tasks.getId()).get();
        updatedTasks.nameEn(UPDATED_NAME_EN).nameAr(UPDATED_NAME_AR).status(UPDATED_STATUS).code(UPDATED_CODE);
        TasksDTO tasksDTO = tasksMapper.toDto(updatedTasks);

        restTasksMockMvc
            .perform(
                put(ENTITY_API_URL_ID, tasksDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(tasksDTO))
            )
            .andExpect(status().isOk());

        // Validate the Tasks in the database
        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeUpdate);
        Tasks testTasks = tasksList.get(tasksList.size() - 1);
        assertThat(testTasks.getNameEn()).isEqualTo(UPDATED_NAME_EN);
        assertThat(testTasks.getNameAr()).isEqualTo(UPDATED_NAME_AR);
        assertThat(testTasks.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testTasks.getCode()).isEqualTo(UPDATED_CODE);
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(tasksSearchRepository.findAll());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
                List<Tasks> tasksSearchList = IterableUtils.toList(tasksSearchRepository.findAll());
                Tasks testTasksSearch = tasksSearchList.get(searchDatabaseSizeAfter - 1);
                assertThat(testTasksSearch.getNameEn()).isEqualTo(UPDATED_NAME_EN);
                assertThat(testTasksSearch.getNameAr()).isEqualTo(UPDATED_NAME_AR);
                assertThat(testTasksSearch.getStatus()).isEqualTo(UPDATED_STATUS);
                assertThat(testTasksSearch.getCode()).isEqualTo(UPDATED_CODE);
            });
    }

    @Test
    void putNonExistingTasks() throws Exception {
        int databaseSizeBeforeUpdate = tasksRepository.findAll().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        tasks.setId(count.incrementAndGet());

        // Create the Tasks
        TasksDTO tasksDTO = tasksMapper.toDto(tasks);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTasksMockMvc
            .perform(
                put(ENTITY_API_URL_ID, tasksDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(tasksDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Tasks in the database
        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithIdMismatchTasks() throws Exception {
        int databaseSizeBeforeUpdate = tasksRepository.findAll().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        tasks.setId(count.incrementAndGet());

        // Create the Tasks
        TasksDTO tasksDTO = tasksMapper.toDto(tasks);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTasksMockMvc
            .perform(
                put(ENTITY_API_URL_ID, count.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(tasksDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Tasks in the database
        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithMissingIdPathParamTasks() throws Exception {
        int databaseSizeBeforeUpdate = tasksRepository.findAll().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        tasks.setId(count.incrementAndGet());

        // Create the Tasks
        TasksDTO tasksDTO = tasksMapper.toDto(tasks);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTasksMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(tasksDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Tasks in the database
        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void partialUpdateTasksWithPatch() throws Exception {
        // Initialize the database
        tasksRepository.save(tasks);

        int databaseSizeBeforeUpdate = tasksRepository.findAll().size();

        // Update the tasks using partial update
        Tasks partialUpdatedTasks = new Tasks();
        partialUpdatedTasks.setId(tasks.getId());

        partialUpdatedTasks.nameAr(UPDATED_NAME_AR);

        restTasksMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTasks.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedTasks))
            )
            .andExpect(status().isOk());

        // Validate the Tasks in the database
        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeUpdate);
        Tasks testTasks = tasksList.get(tasksList.size() - 1);
        assertThat(testTasks.getNameEn()).isEqualTo(DEFAULT_NAME_EN);
        assertThat(testTasks.getNameAr()).isEqualTo(UPDATED_NAME_AR);
        assertThat(testTasks.getStatus()).isEqualTo(DEFAULT_STATUS);
        assertThat(testTasks.getCode()).isEqualTo(DEFAULT_CODE);
    }

    @Test
    void fullUpdateTasksWithPatch() throws Exception {
        // Initialize the database
        tasksRepository.save(tasks);

        int databaseSizeBeforeUpdate = tasksRepository.findAll().size();

        // Update the tasks using partial update
        Tasks partialUpdatedTasks = new Tasks();
        partialUpdatedTasks.setId(tasks.getId());

        partialUpdatedTasks.nameEn(UPDATED_NAME_EN).nameAr(UPDATED_NAME_AR).status(UPDATED_STATUS).code(UPDATED_CODE);

        restTasksMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedTasks.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedTasks))
            )
            .andExpect(status().isOk());

        // Validate the Tasks in the database
        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeUpdate);
        Tasks testTasks = tasksList.get(tasksList.size() - 1);
        assertThat(testTasks.getNameEn()).isEqualTo(UPDATED_NAME_EN);
        assertThat(testTasks.getNameAr()).isEqualTo(UPDATED_NAME_AR);
        assertThat(testTasks.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testTasks.getCode()).isEqualTo(UPDATED_CODE);
    }

    @Test
    void patchNonExistingTasks() throws Exception {
        int databaseSizeBeforeUpdate = tasksRepository.findAll().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        tasks.setId(count.incrementAndGet());

        // Create the Tasks
        TasksDTO tasksDTO = tasksMapper.toDto(tasks);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTasksMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, tasksDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(tasksDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Tasks in the database
        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithIdMismatchTasks() throws Exception {
        int databaseSizeBeforeUpdate = tasksRepository.findAll().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        tasks.setId(count.incrementAndGet());

        // Create the Tasks
        TasksDTO tasksDTO = tasksMapper.toDto(tasks);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTasksMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, count.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(tasksDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Tasks in the database
        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithMissingIdPathParamTasks() throws Exception {
        int databaseSizeBeforeUpdate = tasksRepository.findAll().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        tasks.setId(count.incrementAndGet());

        // Create the Tasks
        TasksDTO tasksDTO = tasksMapper.toDto(tasks);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restTasksMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(tasksDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Tasks in the database
        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void deleteTasks() throws Exception {
        // Initialize the database
        tasksRepository.save(tasks);
        tasksRepository.save(tasks);
        tasksSearchRepository.save(tasks);

        int databaseSizeBeforeDelete = tasksRepository.findAll().size();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        assertThat(searchDatabaseSizeBefore).isEqualTo(databaseSizeBeforeDelete);

        // Delete the tasks
        restTasksMockMvc
            .perform(delete(ENTITY_API_URL_ID, tasks.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<Tasks> tasksList = tasksRepository.findAll();
        assertThat(tasksList).hasSize(databaseSizeBeforeDelete - 1);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tasksSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore - 1);
    }

    @Test
    void searchTasks() throws Exception {
        // Initialize the database
        tasks = tasksRepository.save(tasks);
        tasksSearchRepository.save(tasks);

        // Search the tasks
        restTasksMockMvc
            .perform(get(ENTITY_SEARCH_API_URL + "?query=id:" + tasks.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].nameEn").value(hasItem(DEFAULT_NAME_EN)))
            .andExpect(jsonPath("$.[*].nameAr").value(hasItem(DEFAULT_NAME_AR)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS)))
            .andExpect(jsonPath("$.[*].code").value(hasItem(DEFAULT_CODE)));
    }
}
