import io.github.bitcoinismoral.linkcleaner.MetadataStripper;
import java.nio.file.*;
/** Test helper: strips each input file to <out-dir>/<name>.clean.<ext> and prints what was removed. */
public class StripCli {
    public static void main(String[] a) throws Exception {
        Path outDir = Paths.get(a[0]);
        for (int k = 1; k < a.length; k++) {
            Path in = Paths.get(a[k]);
            MetadataStripper.Result r = MetadataStripper.strip(Files.readAllBytes(in));
            if (r == null) { System.out.println(in.getFileName() + ": UNSUPPORTED"); continue; }
            Path out = outDir.resolve(in.getFileName() + ".clean." + r.extension);
            Files.write(out, r.data);
            System.out.println(in.getFileName() + " -> " + out.getFileName() + "  removed=" + r.removed);
        }
    }
}
