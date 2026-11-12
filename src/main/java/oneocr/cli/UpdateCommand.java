package oneocr.cli;

import jarrunner.jr.apputils.*;
import picocli.CommandLine.*;

import java.util.concurrent.Callable;

import static jarrunner.jr.apputils.UpdateResult.Status.NEWER;

/**
 * {@code 1ocr update}: moves this exe to the newest release. jr does the work (it checks the next version's
 * SHA-256 and swaps the exe, even while it runs); this only asks it and says what it answered.
 *
 * <p>Exit codes: {@code update} 0 when updated or already current, 1 on error; {@code update --check} 0 when current,
 * 10 when a newer version exists, 1 on error. Outside jr (a dev run with {@code java -jar}) there is nothing to update and
 * the message says so.
 */
@Command(name = "update", mixinStandardHelpOptions = true,
    description = "Update 1ocr to the newest release (the exe replaces itself; the new version runs from the next launch)")
public class UpdateCommand implements Callable<Integer> {

    @Option(names = "--check", description = "Only look for a newer version and change nothing (exit 10 when there is one)")
    boolean check;

    @Override
    public Integer call() {
        var r = check ? Updater.check() : Updater.update();
        System.out.println(r.message());
        return r.status() == NEWER ? 10 : r.ok() ? 0 : 1;
    }
}
