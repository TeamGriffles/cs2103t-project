package skeleton;

public interface DispatcherSpec {

    TranslationEngineSpec getTranslationEngine();

    DecisionEngineSpec getDecisionEngine();

    void initialise();

    void start();
}
