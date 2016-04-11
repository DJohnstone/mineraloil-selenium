package example.junit.rules;

import lombok.extern.slf4j.Slf4j;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

@Slf4j
public class OnTestClassFinish extends TestWatcher {

    @Override
    protected void finished(Description description) {
        log.info("FINISH CLASS: " + description);
        super.finished(description);
    }

}
