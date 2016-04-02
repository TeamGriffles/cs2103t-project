package logic;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import com.google.gson.internal.LinkedTreeMap;
import javafx.util.Pair;
import shared.Command;
import shared.Resources;
import skeleton.CommandParserSpec;

/**
 * Created by maianhvu on 31/03/2016.
 */
public class FlexiCommandParser implements CommandParserSpec {

    private static final String NAME_FILE_DATA = "CommandParserData.json";

    /**
     * Types
     */
    private enum TimePrepositionMeaning {
        CURRENT, NEXT, STARTING, ENDING
    }
    private enum TimeNounMeaning {
        NOW, TODAY, TOMORROW, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
    }

    /**
     * Singleton implementation
     */
    private static FlexiCommandParser instance = new FlexiCommandParser();

    public static FlexiCommandParser getInstance() {
        return instance;
    }

    /**
     * Properties
     */
    private HashMap<String, Command.Instruction> _instructionMap;
    private HashMap<String, TimePrepositionMeaning> _timePrepositionMeaningMap;
    private HashMap<String, TimeNounMeaning> _timeNounMeaningMap;
    private Definition _commandDefinitions;

    private FlexiCommandParser() {}

    public void initialize() {
        this.prepareInstructions();
        this.prepareTimePrepositions();
        this.prepareTimeDefinitions();

        this.readDefinitions();
        this.constructRegex();
    }

    /*=================================================================================================
     |                                                                                                |
     | ENUM CLASS INVERSION TABLE PREPARATIONS                                                        |
     |                                                                                                |
     +-------------------------------------------------------------------------------------------------
     |                                                                                                |
     | This section constructs hash maps that points a constant string name back to                   |
     | the original enum constant with the corresponding name. This is usually used                   |
     | in JSON deserializations.                                                                      |
     |                                                                                                |
     *===============================================================================================*/
    private void prepareInstructions() {
        this._instructionMap = constructNameMap(Command.Instruction.class);
    }

    private void prepareTimePrepositions() {
        this._timePrepositionMeaningMap = constructNameMap(TimePrepositionMeaning.class);
    }

    private void prepareTimeDefinitions() {
        this._timeNounMeaningMap = constructNameMap(TimeNounMeaning.class);
    }

    private static <T extends Enum> HashMap<String, T> constructNameMap(Class<T> enumClass) {
        HashMap<String, T> map = new HashMap<>();
        Arrays.stream(enumClass.getEnumConstants())
                .forEach(meaning -> map.put(
                        meaning.name(),
                        meaning
                ));
        return map;
    }

    /*=================================================================================================
     |                                                                                                |
     | READING JSON DEFINITIONS                                                                       |
     |                                                                                                |
     +-------------------------------------------------------------------------------------------------
     |                                                                                                |
     | This section reads the JSON files from the `Resource` folder and parse the                     |
     | correct data into the `Definition` private class (nested under this class).                    |
     | The parsed `Definition` class will then assist in both parsing the command,                    |
     | and highlighting the command keywords inside the command input field.                          |
     |                                                                                                |
     *===============================================================================================*/
    private void readDefinitions() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Definition.class, new DefinitionDeserializer());
        Gson gson = builder.create();

        // Deserialize
        String definitionData = Resources.getInstance().getDataFrom(NAME_FILE_DATA);
        Definition definition = gson.fromJson(definitionData, Definition.class);

        this._commandDefinitions = definition;
    }

    /*=================================================================================================
     |                                                                                                |
     | PUBLIC GET API FOR KEYWORDS                                                                    |
     |                                                                                                |
     +-------------------------------------------------------------------------------------------------
     |                                                                                                |
     | This allows other classes to access the data deserialized from the JSON file.                  |
     |                                                                                                |
     *===============================================================================================*/
    public Set<String> getInstructions() {
        assert this._commandDefinitions != null;
        return this._commandDefinitions.getInstructions();
    }

    public Map<String, TimeClause> getTimeClauses() {
        assert this._commandDefinitions != null;
        return this._commandDefinitions.getTimeClauses();
    }
    /*=================================================================================================
     |                                                                                                |
     | CONSTRUCTION OF REGEX                                                                          |
     |                                                                                                |
     +-------------------------------------------------------------------------------------------------
     |                                                                                                |
     | The regular expressions to parse the commands, as well as to highlight them                    |
     | inside the command input box, will all be constructed in the methods within                    |
     | this section.                                                                                  |
     |                                                                                                |
     *===============================================================================================*/
    private void constructRegex() {
    }

    /*=================================================================================================
     |                                                                                                |
     | JSON DEFINITION CLASS                                                                          |
     |                                                                                                |
     +-------------------------------------------------------------------------------------------------
     |                                                                                                |
     | This class represents the definitions data that have been abstracted after                     |
     | deserialization from the raw JSON file. This class also contains some helper                   |
     | algorithms to help create some of the regular expressions above.                               |
     |                                                                                                |
     *===============================================================================================*/
    private class Definition {
        /**
         * Properties
         */
        private HashMap<Command.Instruction, Set<String>> _commandKeywordMap;
        private HashMap<TimePrepositionMeaning, Set<String>> _timePrepositionMap;
        private HashMap<TimeNounMeaning, Set<String>> _timeNounMap;
        private Map<TimeNounMeaning, List<TimePrepositionMeaning>> _adjacencyList;

        // Caching
        private Set<String> _cachedInstructions;
        private Map<String, TimeClause> _cachedTimeClauseMap;

        Definition() {
            this._commandKeywordMap = new LinkedHashMap<>();
            this._timePrepositionMap = new LinkedHashMap<>();
            this._timeNounMap = new LinkedHashMap<>();
            this._adjacencyList = new LinkedTreeMap<>();
        }

        void addInstruction(String name, String[] keywords) {
            final Command.Instruction instruction = _instructionMap.get(name);
            final Set<String> keywordSet = new CopyOnWriteArraySet<>(Arrays.asList(keywords));
            this._commandKeywordMap.put(instruction, keywordSet);
        }

        void addTimePreposition(String meaning, String[] keywords) {
            final TimePrepositionMeaning realMeaning = _timePrepositionMeaningMap.get(meaning);
            final Set<String> keywordSet = new CopyOnWriteArraySet<>(Arrays.asList(keywords));
            this._timePrepositionMap.put(realMeaning, keywordSet);
        }

        void addTimeNoun(String meaning, String[] keywords, String[] prepositions) {
            final TimeNounMeaning realMeaning = _timeNounMeaningMap.get(meaning);
            final Set<String> keywordSet = new CopyOnWriteArraySet<>(Arrays.asList(keywords));
            this._timeNounMap.put(realMeaning, keywordSet);

            // Add time nouns to adjacency matrix
            final List<TimePrepositionMeaning> prepositionsList = Arrays.stream(prepositions)
                    .map(_timePrepositionMeaningMap::get)
                    .collect(Collectors.toList());
            this._adjacencyList.put(realMeaning, prepositionsList);
        }

        Map<String, TimeClause> getTimeClauses() {
            if (this._cachedTimeClauseMap != null) {
                return this._cachedTimeClauseMap;
            }

            // Initialize the map
            Map<String, TimeClause> timeClauseMap = new LinkedHashMap<>();

            // Run through the adjacency list to construct the map
            this._adjacencyList.entrySet().forEach(entry -> {
                // Get all nouns
                Set<String> timeNouns = this._timeNounMap.get(entry.getKey());
                // Use all the prepositions to construct the time clause map
                entry.getValue().stream()
                        .map(preposition -> new Pair<>(
                                new TimeClause(preposition, entry.getKey()),
                                this._timePrepositionMap.get(preposition)
                        ))
                        .forEach(pair -> {
                            TimeClause clause = pair.getKey();
                            Set<String> prepositions = pair.getValue();

                            prepositions.stream().flatMap(preposition ->
                                    timeNouns.stream().map(noun -> String.format("%s %s", preposition, noun))
                            ).forEach(
                                    stringClause -> timeClauseMap.put(stringClause, clause)
                            );
                        });
            });

            // Cache
            this._cachedTimeClauseMap = timeClauseMap;

            return timeClauseMap;
        }

        Set<String> getInstructions() {
            // Return cache right away
            if (this._cachedInstructions != null) {
                return this._cachedInstructions;
            }

            // Construct instructions set
            this._cachedInstructions = this._commandKeywordMap.values().stream()
                    .map(ArrayList::new)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());

            return this._cachedInstructions;
        }
    }

    /**
     *
     */
    private class DefinitionDeserializer implements JsonDeserializer<Definition> {

        @Override public Definition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            // Get the definition as a JSON object
            final JsonObject definitionObject = json.getAsJsonObject();
            final Definition definition = new Definition();

            // Begin deserialization

            // INSTRUCTIONS ARRAY
            // This array contains the information about command instructions and their alternative forms
            final JsonArray instructionsArray = definitionObject.get("instructions").getAsJsonArray();
            instructionsArray.forEach(instructionElement -> {
                final JsonObject instructionObject = instructionElement.getAsJsonObject();

                final String instructionName = instructionObject.get("name").getAsString();
                final String[] instructionKeywords = decodeJSONStringArray(
                        instructionObject.get("keywords").getAsJsonArray()
                );

                definition.addInstruction(instructionName, instructionKeywords);
            });

            // TIME PREPOSITIONS ARRAY
            // This array dictates the behaviour of the prepositions which modify the time word which follows
            // them inside a command string
            final JsonArray timePrepositionsArray = definitionObject.get("timePrepositions").getAsJsonArray();
            timePrepositionsArray.forEach(timePrepositionElement -> {
                // Map to an actual JSON object
                final JsonObject timePrepositionObject = timePrepositionElement.getAsJsonObject();

                final String meaning = timePrepositionObject.get("meaning").getAsString();
                final String[] timePrepositionMeaningKeywords = decodeJSONStringArray(
                        timePrepositionObject.get("keywords").getAsJsonArray()
                );

                definition.addTimePreposition(meaning, timePrepositionMeaningKeywords);
            });

            // TIME ARRAY
            // This array defines the different types of days that are allowed
            final JsonArray timeArray = definitionObject.get("timeNouns").getAsJsonArray();
            timeArray.forEach(timeElement -> {
                // Map to an actual JSON object
                final JsonObject timeObject = timeElement.getAsJsonObject();

                final String meaning = timeObject.get("meaning").getAsString();
                final String[] keywords = decodeJSONStringArray(
                        timeObject.get("keywords").getAsJsonArray()
                );
                final String[] prepositions = decodeJSONStringArray(
                        timeObject.get("prepositions").getAsJsonArray()
                );

                definition.addTimeNoun(meaning, keywords, prepositions);
            });

            return definition;
        }

    }

    private class TimeClause {
        private TimePrepositionMeaning _preposition;
        private TimeNounMeaning _noun;

        TimeClause(TimePrepositionMeaning preposition, TimeNounMeaning noun) {
            this._preposition = preposition;
            this._noun = noun;
        }

        TimePrepositionMeaning getPreposition() {
            return this._preposition;
        }

        TimeNounMeaning getNoun() {
            return this._noun;
        }

        @Override public String toString() {
            return String.format("%s %s", this._preposition.name(), this._noun.name());
        }
    }

    /*=================================================================================================
     |                                                                                                |
     | MAIN PARSE METHOD                                                                              |
     |                                                                                                |
     +-------------------------------------------------------------------------------------------------
     |                                                                                                |
     | Core method that pieces everything together to construct a Command from a raw query.           |
     |                                                                                                |
     *===============================================================================================*/

    /**
     * TODO: Write JavaDoc
     * @param commandString
     * @return
     */
    @Override public Command parse(String commandString) {
        // Ensure command string is ready
        assert commandString != null;
        commandString = commandString.trim();

        String instructionWord = commandString.substring(0, commandString.indexOf(' '));
        Command.Instruction instruction = null; // this._commandDefinitions.getInstruction(instructionWord);
        if (instruction == null) {
            return new Command(Command.Instruction.UNRECOGNISED, null, false);
        }

        return new Command(instruction, null, false);
    }

    /*=================================================================================================
     |                                                                                                |
     | HELPER METHODS                                                                                 |
     |                                                                                                |
     +-------------------------------------------------------------------------------------------------
     |                                                                                                |
     | Supporting methods that make other functions maintain brevity and efficiency.                  |
     |                                                                                                |
     *===============================================================================================*/
    private static String[] decodeJSONStringArray(JsonArray stringArray) {
        final String[] array = new String[stringArray.size()];
        IntStream.range(0, array.length).forEachOrdered(index -> {
            array[index] = stringArray.get(index).getAsString();
        });
        return array;
    }

    private static String constructChoiceRegex(String[] choices) {
        StringBuilder sb = new StringBuilder("(?:");
        for (int i = 0; i < choices.length; i++) {
            if (i != 0) {
                sb.append("|");
            }
            sb.append(choices[i]);
        }
        sb.append(")");
        return sb.toString();
    }
}
