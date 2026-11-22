import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Signs (v2 + v3) and verifies an APK using AOSP's open-source apksig library. */
public class Sign {
    public static void main(String[] args) throws Exception {
        File in = new File(args[0]), out = new File(args[1]), ksFile = new File(args[2]);
        char[] pw = new String(Files.readAllBytes(Paths.get(args[3]))).trim().toCharArray();

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream f = new FileInputStream(ksFile)) { ks.load(f, pw); }
        PrivateKey key = (PrivateKey) ks.getKey("xlinkcleaner", pw);
        X509Certificate cert = (X509Certificate) ks.getCertificate("xlinkcleaner");

        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder(
                "xlinkcleaner", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(sc))
                .setInputApk(in).setOutputApk(out)
                .setV1SigningEnabled(false).setV2SigningEnabled(true).setV3SigningEnabled(true)
                .build().sign();

        // Verify without telling it the min SDK, so it also has to parse our binary manifest.
        ApkVerifier.Result r = new ApkVerifier.Builder(out).build().verify();
        System.out.println("verified=" + r.isVerified() + " v2=" + r.isVerifiedUsingV2Scheme() + " v3=" + r.isVerifiedUsingV3Scheme());
        r.getErrors().forEach(e -> System.out.println("ERROR " + e));
        r.getWarnings().forEach(w -> System.out.println("WARN  " + w));
        if (!r.isVerified()) System.exit(1);
    }
}
