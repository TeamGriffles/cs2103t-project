package logic;

import javafx.util.Pair;
import shared.*;
import skeleton.CollectionSpec;
import skeleton.DecisionEngineSpec;
import skeleton.SchedulerSpec;
import storage.Storage;
import storage.TaskPriorityComparator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @@author Thenaesh Elango
 */
public class DecisionEngine implements DecisionEngineSpec {
    public static final double THRESHOLD_POWERSEARCH_WEIGHTED = 0.0;
    /**
     * Singleton instance and constructor
     */
    private static DecisionEngine instance;

    private DecisionEngine() {
    }

    /**
     * instance fields
     */
    public static DecisionEngine getInstance() {
        if (instance == null) {
            instance = new DecisionEngine();
        }

        return instance;
    }

    @Override public void initialise() {
        Storage.getInstance().readFromDisk();
    }


    protected ExecutionResult displayAllTasks() {
        List<Task> listToDisplay = this.getTaskCollection().getAll().stream()
                .sorted(TaskPriorityComparator.getInstance()).collect(Collectors.toList());

        return new ExecutionResult(ViewType.TASK_LIST, listToDisplay);
    }

    protected ExecutionResult handleDisplay(Command command) {
        assert command.hasInstruction(Command.Instruction.DISPLAY);

        List<Task> listToDisplay = this.getTaskCollection().getAll();

        // for each command parameter, filter the list of tasks
        if (command.hasParameter(Command.ParamName.TASK_NAME)) {
            String name = command.getParameter(Command.ParamName.TASK_NAME);
            listToDisplay = listToDisplay
                    .stream()
                    .filter(task -> task.getTaskName() == name)
                    .collect(Collectors.toList());
        }
        if (command.hasParameter(Command.ParamName.TASK_START)) {
            CustomTime from = command.getParameter(Command.ParamName.TASK_START);
            listToDisplay = listToDisplay
                    .stream()
                    .filter(task -> from.compareTo(task.getStartTime()) <= 0)
                    .collect(Collectors.toList());
        }
        if (command.hasParameter(Command.ParamName.TASK_END)) {
            CustomTime to = command.getParameter(Command.ParamName.TASK_END);
            listToDisplay = listToDisplay
                    .stream()
                    .filter(task -> to.compareTo(task.getEndTime()) >= 0)
                    .collect(Collectors.toList());
        }

        // at this point, we have a properly filtered list
        return new ExecutionResult(ViewType.TASK_LIST, listToDisplay);
    }


    protected ExecutionResult handleSearch(Command command) {
        assert command.hasInstruction(Command.Instruction.SEARCH);

        // PowerSearching!
        Pattern pattern = buildPowerSearchPattern(command);
        double averageQueryLength = getAverageQueryLength(command);

        List<Task> foundTask = this.getTaskCollection().getAll().stream().map(item -> {
            // Match with task name first
            Matcher m = pattern.matcher(item.getTaskName());
            int matches = 0;
            List<Double> similarityIndex = new ArrayList<>();

            while (m.find()) {
                matches++;
                double similarity = averageQueryLength / m.group("MATCH").length();
                if (similarity > 1.0) {
                    similarity = 1.0;
                }
                similarityIndex.add(similarity);
            }

            double weightedMatch = matches * similarityIndex.stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0.0);
            return new Pair<>(weightedMatch, item);
        }).filter(pair -> pair.getKey() > THRESHOLD_POWERSEARCH_WEIGHTED)
                .sorted((pair1, pair2) -> pair2.getKey().compareTo(pair1.getKey()))
                .map(Pair::getValue)
                .collect(Collectors.toList());

        return new ExecutionResult(ViewType.TASK_LIST, foundTask);
    }



    @Override public ExecutionResult performCommand(Command command) {

        // this sort of nonsense should have been handled in the front end
        assert !command.hasInstruction(Command.Instruction.INVALID);
        assert !command.hasInstruction(Command.Instruction.UNRECOGNISED);

        // handle exit command here, without creating a task unnecessarily
        if (command.hasInstruction(Command.Instruction.EXIT)) {
            return ExecutionResult.shutdownSignal();
        }

        // Prepare final execution result to be returned
        ExecutionResult result = null;

        // all the standard commands
        switch (command.getInstruction()) {
            case ADD:
            case DELETE:
            case EDIT:
            case MARK:
                StorageWriteOperation op = new StorageWriteOperation(command);
                op.getInitialOperation().apply(null);
                StorageWriteOperationHistory.getInstance().addToHistory(op);
                result = this.displayAllTasks();
                break;
            case DISPLAY:
                result = this.handleDisplay(command);
                break;
            case SEARCH:
                result = this.handleSearch(command);
                break;
            case UNDO:
                boolean undoActuallyHappened = StorageWriteOperationHistory.getInstance().undo();
                result = this.displayAllTasks();
                if (!undoActuallyHappened) {
                    result.setErrorMessage("No tasks to undo!");
                }
                break;
            case REDO:
                boolean redoActuallyHappened = StorageWriteOperationHistory.getInstance().redo();
                result = this.displayAllTasks();
                if (!redoActuallyHappened) {
                    result.setErrorMessage("No tasks to redo");
                }
                break;
            default:
                // if we reach this point, LTA Command Parser has failed in his duty
                // and awaits court martial
                assert false;
        }

        return result;
    }

    @Override public SchedulerSpec getTaskScheduler() {
        return Scheduler.getInstance();
    }

    @Override public void shutdown() {
        Storage.getInstance().writeToDisk();
    }

    @Override public CollectionSpec<Task> getTaskCollection() {
        return Storage.getInstance();
    }

    private static Pattern buildPowerSearchPattern(Command command) {
        String query = command.getParameter(Command.ParamName.SEARCH_QUERY);
        // Split the query into words
        String[] words = query.split("\\s+");

        // Begin building pattern by signalling that we are looking for
        // a word that contains the characters
        StringBuilder patternBuilder = new StringBuilder();
        patternBuilder.append("\\b(?<MATCH>");

        // In that particular order. We achieve this by inserting
        // greedy word (\w*) pattern, slotted between the characters of
        // each of the query word
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (i != 0) {
                patternBuilder.append("|");
            }

            for (int j = 0; j < word.length(); j++) {
                patternBuilder.append("\\w*");
                patternBuilder.append(word.charAt(j));
            }
            patternBuilder.append("\\w*"); // At the end too
        }

        // Conclude the pattern
        patternBuilder.append(")\\b");
        return Pattern.compile(patternBuilder.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static double getAverageQueryLength(Command command) {
        String query = command.getParameter(Command.ParamName.SEARCH_QUERY);
        return Arrays.asList(query.split("\\s+")).stream()
                .mapToDouble(String::length)
                .average().orElse(0.0);
    }
}
