package component.back_end;

import entity.*;
import entity.command.Command;


public class DecisionEngine implements DecisionEngineSpec {

    @Override
    public ExecutionResult<?> performCommand(Command command) {
        
        switch (command.getInstruction().getType()) {
            case ADD:
                // TODO
                break;
            case EDIT:
                // TODO
                break;
            case DISPLAY:
                // TODO
                break;
            case DELETE:
                // TODO
                break;
            case EXIT:
                // TODO
                break;
            case UNRECOGNISED:
                // TODO
                break;
            default:
                // if we reach this point, LTA Command Parser has failed in his duty
                assert false;
        }
        return null;
        
    }
    
    public void addTask(Task task) {
    }
    
    public void editTask(Task task) {
    }
    
    public void displayTasks(){
    }

}
