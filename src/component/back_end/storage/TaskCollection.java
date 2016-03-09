package component.back_end.storage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;

import exception.back_end.PrimaryKeyNotFoundException;

/**
 * Created by maianhvu on 7/3/16.
 */

public class TaskCollection {
    
    private final boolean SEARCH_MAP_BY_DATETIME_INCLUSIVE = true;

    private TreeMap<Integer, Task> taskData_;
    private TreeMap<LocalDateTime, List<Task>> startTimeTree_;
    private TreeMap<LocalDateTime, List<Task>> endTimeTree_;

    public TaskCollection() {
        this.taskData_ = new TreeMap<>();
        this.startTimeTree_ = new TreeMap<>();
        this.endTimeTree_ = new TreeMap<>();
    }
    
    //----------------------------------------------------------------------------------------
    //
    // I. Save Method
    //
    //----------------------------------------------------------------------------------------

    /**
     * Saves a Task object to TreeMap
     * @param task the Task to put into TreeMap
     * @return the ID of the added Task
     */
    public int save(Task task) {
        // TODO: Check for potential time clashes
        boolean isNewTask = false; 
        
        if (task.getId() == null) {
            isNewTask = true;
            
            // TODO: Extract magic constant
            int newIndex = 1;

            if (!this.taskData_.isEmpty()) {
                // TODO: Extract magic constant
                newIndex = this.taskData_.lastKey() + 1;
            }

            task.setId(newIndex);
        }
        
        if (!isNewTask) {
            // extract the old Task entry from the tree
            Task oldTask = this.taskData_.get(task.getId());
            this.addTaskToStartTimeTree(isNewTask, task, oldTask);
            this.addTaskToEndTimeTree(isNewTask, task, oldTask);
        } else {
            this.addTaskToStartTimeTree(isNewTask, task, null);
            this.addTaskToEndTimeTree(isNewTask, task, null);
        }

        // add Task entry to the tree that maps ID to Task
        this.taskData_.put(task.getId(), task);

        return task.getId();
    }
    
    public void addTaskToStartTimeTree(boolean isNewTask, Task newTask, Task oldTask) {
        if (!isNewTask) {
            this.processOldTaskInStartTimeTree(newTask, oldTask);
        }
        this.processNewTaskInStartTimeTree(newTask);
    }
    
    public void processOldTaskInStartTimeTree(Task newTask, Task oldTask) {
        // if oldTask has startTime, then oldTask currently exists in startTimeTree
        if (oldTask.getStartTime() != null) {
            // remove oldTask from startTimeTree
            this.startTimeTree_.get(oldTask.getStartTime()).remove(oldTask);
        }
    }
    
    public void processNewTaskInStartTimeTree(Task newTask) {
        // do not add any Task with null start time to this tree
        if (newTask.getStartTime() != null) {
            if (!this.startTimeTree_.containsKey(newTask.getStartTime())) {
                // if key yet to exist, put it into tree and create a list
                this.startTimeTree_.put(newTask.getStartTime(), new ArrayList<Task>());
            }
            // append Task to end of list
            this.startTimeTree_.get(newTask.getStartTime()).add(newTask);
        }
    }
    
    public void addTaskToEndTimeTree(boolean isNewTask, Task newTask, Task oldTask) {
        if (!isNewTask) {
            this.processOldTaskInEndTimeTree(newTask, oldTask);
        }
        this.processNewTaskInEndTimeTree(newTask);
    }
    
    public void processOldTaskInEndTimeTree(Task newTask, Task oldTask) {
        // if oldTask has endTime, then oldTask currently exists in endTimeTree
        if (oldTask.getEndTime() != null) {
            this.endTimeTree_.get(oldTask.getEndTime()).remove(oldTask);
        }
    }

    public void processNewTaskInEndTimeTree(Task newTask) {
        // do not add any Task with null end time to this tree
        if (newTask.getEndTime() != null) {
            if (!this.endTimeTree_.containsKey(newTask.getEndTime())) {
                // if key yet to exist, put it into the tree and create a list
                this.endTimeTree_.put(newTask.getEndTime(), new ArrayList<Task>());
            }
            // append Task to end of list
            this.endTimeTree_.get(newTask.getEndTime()).add(newTask);
        }
    }
    
    //----------------------------------------------------------------------------------------
    //
    // II. Get Method
    //
    //----------------------------------------------------------------------------------------

    /**
     * Returns the Task to which the specified index is mapped
     * @param index the index whose associated Task is to be returned
     * @return the Task to which the specified index is mapped
     * @throws PrimaryKeyNotFoundException if the TreeMap contains no mapping for the index
     */
    public Task get(int index) throws PrimaryKeyNotFoundException {
        // check if TreeMap contains the key that is queried
        if (!this.taskData_.containsKey(index)) {
            throw new PrimaryKeyNotFoundException(index);
        }
        // key exists, retrieve Task corresponding to key
        return this.taskData_.get(index);
    }
    
    //----------------------------------------------------------------------------------------
    //
    // III. GetAll Method
    //
    //----------------------------------------------------------------------------------------
    
    /**
     * Returns a filtered list of Tasks that match the specified TaskDescriptor
     * Returns the full (unfiltered) list of Tasks when no TaskDescriptor is specified 
     * @param taskDescriptor the TaskDescriptor that determines the criteria for entry match
     * @return results which is a list of filtered Tasks that matches TaskDescriptor if one is specified,
     * else results is the full list of Tasks stored in TreeMap
     */
    public List<Task> getAll(TaskDescriptor taskDescriptor) {
        ArrayList<Task> results = new ArrayList<>(this.taskData_.values());
        
        // when no task descriptor is specified, taskDescriptor is null
        if (taskDescriptor == null) {
            return results;
        }
        
        Iterator<Task> it = results.iterator();
        while (it.hasNext()) {
            Task task = it.next();
            if (!taskDescriptor.matches(task)) {
                // remove Task that does not match TaskDescriptor
                it.remove();
            }
        }
        
        // return filtered results as a List
        return results;
    }
    
    //----------------------------------------------------------------------------------------
    //
    // IV. Remove Method
    //
    //----------------------------------------------------------------------------------------

    /**
     * Removes the mapping for this index from TreeMap if present
     * @param id index for which mapping should be removed
     * @return the previous Task associated with id, or null if there was no mapping for id
     */
    public Task remove(int id) {
        // TODO: Check if ID does not exist

        return this.taskData_.remove(id);
    }
    
    //----------------------------------------------------------------------------------------
    //
    // V. Search by Time Range Methods
    //
    //----------------------------------------------------------------------------------------
    
    /**
     * Search for all Tasks that has startTime before or at the same time as dateTime
     * @param dateTime high endpoint (inclusive) of the Tasks in the returned list
     * @return a list of Tasks that starts before or at the same time as dateTime
     */
    public List<Task> searchstartBefore(LocalDateTime dateTime) {
        NavigableMap<LocalDateTime, List<Task>> resultsMap = this.startTimeTree_.headMap(dateTime, SEARCH_MAP_BY_DATETIME_INCLUSIVE);
        return this.extractByDateTime(resultsMap);
    }
    
    /**
     * Search for all Tasks that has startTime after or at the same time as dateTime
     * @param dateTime low endpoint (inclusive) of the Tasks in the returned list
     * @return a list of Tasks that starts after or at the same time as dateTime
     */
    public List<Task> searchStartAfter(LocalDateTime dateTime) {
        NavigableMap<LocalDateTime, List<Task>> resultsMap = this.startTimeTree_.tailMap(dateTime, SEARCH_MAP_BY_DATETIME_INCLUSIVE);
        return this.extractByDateTime(resultsMap);
    }
    
    /**
     * Search for all Tasks that has endTime before or at the same time as dateTime
     * @param dateTime high endpoint (inclusive) of the Tasks in the returned list
     * @return a list of Tasks that ends before or at the same time as dateTime
     */
    public List<Task> searchEndBefore(LocalDateTime dateTime) {
        NavigableMap<LocalDateTime, List<Task>> resultsMap = this.endTimeTree_.headMap(dateTime, SEARCH_MAP_BY_DATETIME_INCLUSIVE);
        return this.extractByDateTime(resultsMap);
    }
    
    /**
     * Search for all Tasks that has endTime after or at the same time as dateTime
     * @param dateTime low endpoint (inclusive) of the Tasks in the returned list
     * @return a list of Tasks that ends after or at the same time as dateTime
     */
    public List<Task> searchEndAfter(LocalDateTime dateTime) {
        NavigableMap<LocalDateTime, List<Task>> resultsMap = this.endTimeTree_.tailMap(dateTime, SEARCH_MAP_BY_DATETIME_INCLUSIVE);
        return this.extractByDateTime(resultsMap);
    }
    
    /**
     * Search for all Tasks that have startTime or endTime between start and end (inclusive)
     * @param start low endpoint (inclusive) of the Tasks in the returned list
     * @param end high endpoint (inclusive) of the Tasks in the returned list
     * @return a list of Tasks that start or end within the time range start to end (inclusive)
     */
    public List<Task> searchByDateTimeRange(LocalDateTime start, LocalDateTime end) {
        // collection of all qualifying Tasks that contains no duplicate elements
        HashSet<Task> resultsHashSet = new HashSet<Task>();
        
        NavigableMap<LocalDateTime, List<Task>> startTimeTreeResultsMap = this.startTimeTree_.subMap
                (start, SEARCH_MAP_BY_DATETIME_INCLUSIVE, end, SEARCH_MAP_BY_DATETIME_INCLUSIVE); 
        resultsHashSet.addAll(this.extractByDateTime(startTimeTreeResultsMap));
        
        NavigableMap<LocalDateTime, List<Task>> endTimeTreeResultsMap = this.endTimeTree_.subMap
                (start, SEARCH_MAP_BY_DATETIME_INCLUSIVE, end, SEARCH_MAP_BY_DATETIME_INCLUSIVE);
        resultsHashSet.addAll(this.extractByDateTime(endTimeTreeResultsMap));
        
        ArrayList<Task> resultList = new ArrayList<Task>(resultsHashSet);
        Collections.sort(resultList);
        
        return resultList;
    }
    
    /**
     * Extracts all Tasks whose indices are contained in resultsMap
     * @param resultsMap a view of the portion of TreeMap which has startTime and/or endTime 
     * falling within a certain time range.
     * @return list of Tasks that satisfy the time range condition
     */
    public List<Task> extractByDateTime(NavigableMap<LocalDateTime, List<Task>> resultsMap) {
        ArrayList<Task> resultList = new ArrayList<Task>();
        NavigableSet<LocalDateTime> resultsKeySet = resultsMap.navigableKeySet();
        
        Iterator<LocalDateTime> it = resultsKeySet.iterator();
        while (it.hasNext()) {
            LocalDateTime key = it.next();
            List<Task> values = resultsMap.get(key);
            resultList.addAll(values);
        }
        return resultList;
    }

    
    public TreeMap<LocalDateTime, List<Task>> getStartTimeTree() {
        return startTimeTree_;
    }

    
    public void setStartTimeTree(TreeMap<LocalDateTime, List<Task>> startTimeTree) {
        startTimeTree_ = startTimeTree;
    }

    
    public TreeMap<LocalDateTime, List<Task>> getEndTimeTree() {
        return endTimeTree_;
    }

    
    public void setEndTimeTree(TreeMap<LocalDateTime, List<Task>> endTimeTree) {
        endTimeTree_ = endTimeTree;
    }
    
    
}
