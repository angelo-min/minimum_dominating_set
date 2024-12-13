//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package algorithms;

import supportGUI.Evaluator;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class CustomEvaluator {
    private static String testFolder = "/files/archives/aaga2015/dominatingSetDG/tests";
    private static int edgeThreshold = 55;
    private static boolean proxyPPTI = false;
    private static AtomicLong result;
    private static AtomicInteger resultCount;
    private static int fails;

    public static void main(String[] args) {
        for(int i = 0; i < args.length; ++i) {
            if (args[i].charAt(0) == '-') {
                if (args[i + 1].charAt(0) == '-') {
                    System.err.println("Option " + args[i] + " expects an argument but received none");
                    return;
                }

                switch (args[i]) {
                    case "-test":
                        try {
                            testFolder = args[i + 1];
                            break;
                        } catch (Exception var6) {
                            System.err.println("Invalid argument for option " + args[i] + ": TestBed folder expected");
                            return;
                        }
                    case "-proxyPPTI":
                        proxyPPTI = true;
                        break;
                    default:
                        System.err.println("Unknown option " + args[i]);
                        return;
                }

                ++i;
            }
        }

        evalFiles(proxyPPTI);
    }

    protected static double getResult() {
        return Double.longBitsToDouble(result.get());
    }

    protected static int getFails() {
        return fails;
    }

    protected static void evalFiles(boolean proxyPPTI) {
        result = new AtomicLong(Double.doubleToLongBits(0.0));
        resultCount = new AtomicInteger(0);
        fails = 0;
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        for(int i = 0; i < 100; ++i) {
            int index = i;
            pool.execute(() -> {
                ArrayList<Point> points = new ArrayList();

                try {
                    if (proxyPPTI) {
                        System.setProperty("http.proxyHost", "proxy.ufr-info-p6.jussieu.fr");
                        System.setProperty("http.proxyPort", "3128");
                        System.setProperty("https.proxyHost", "proxy.ufr-info-p6.jussieu.fr");
                        System.setProperty("https.proxyPort", "3128");
                    }

                    TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }};
                    SSLContext sc = SSLContext.getInstance("SSL");
                    sc.init((KeyManager[]) null, trustAllCerts, new SecureRandom());
                    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                    HostnameVerifier allHostsValid = new HostnameVerifier() {
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    };
                    HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
                    InputStream url = (new URL("https://www-npa.lip6.fr/~buixuan" + testFolder + "/input" + index + ".points")).openStream();
                    BufferedReader input = new BufferedReader(new InputStreamReader(url));

                    try {
                        String line;
                        while ((line = input.readLine()) != null) {
                            String[] coordinates = line.split("\\s+");
                            points.add(new Point(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1])));
                        }

                        System.out.println("Input " + index + " successfully read. Computing...");
                        ArrayList<Point> pts = (new DefaultTeam()).calculDominatingSet(points, edgeThreshold);
                        System.out.println("   >>> Computation completed. Evaluating... ");
                        if (!Evaluator.isValide(pts, points, edgeThreshold)) {
                            ++fails;
                        } else {
                            long expected, calculated;
                            do {
                                expected = result.get();
                                calculated = Double.doubleToLongBits(Double.longBitsToDouble(expected) + Evaluator.score(pts));
                            } while (!result.compareAndSet(expected, calculated));
                            resultCount.addAndGet(1);
                        }

                        System.out.println("   >>> Evaluation completed. Fails: " + fails);
                        double var10001 = Double.longBitsToDouble(result.get()) / resultCount.get();
                        System.out.println("   >>> Average score (excluding fails): " + var10001);
                    } catch (IOException var20) {
                        System.err.println("Exception: interrupted I/O.");
                    } finally {
                        try {
                            input.close();
                        } catch (IOException var19) {
                            System.err.println("I/O exception: unable to close files.");
                        }

                    }
                } catch (Exception var22) {
                    Exception e = var22;
                    ++fails;
                    if (!(e instanceof FileNotFoundException) && !(e instanceof IOException)) {
                        System.err.println("Computation aborted with an exception." + e);
                    } else {
                        System.err.println("Input file not found.");
                    }
                }
            });
        }
        try {
            pool.awaitTermination(1_000_000, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("--------------------------------------");
        System.out.println("");
        System.out.println("Total fails: " + fails);
        System.out.println("Average score: " + Double.longBitsToDouble(result.get()) / resultCount.get());
    }
}
