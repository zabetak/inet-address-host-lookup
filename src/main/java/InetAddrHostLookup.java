import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Security;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A program performing InetAddress resolution based on hostname concurrently.
 *
 * The program simulates a real multi-threaded application that performs hostname resolution using the 
 * {@link InetAddress#getByName(String)} method. It is primary purpose is to help diagnose a native memory leak 
 * appearing in an old version of Hiveserver2 that seems to be related with address resolution.   
 */
public class InetAddrHostLookup {

  public static void main(String[] args) throws InterruptedException, ParseException {
    Options opts = new Options();
    opts.addOption("poolSize", true, "Size of thread pool (Default 1)");
    opts.addOption("maxSize", true, "Max size of thread pool (Default 1)");
    opts.addOption("executions", true, "Total number of executions (Default unlimited)");
    opts.addOption("delayOnReject", true,
        "Time (ms) to wait before submitting another lookup when thread pool gets full (Default 1000)");
    Option o = new Option("host", true, "Host to resolve address");
    o.setRequired(true);
    opts.addOption(o);

    CommandLineParser parser = new BasicParser();
    CommandLine line;
    try {
      line = parser.parse(opts, args);
    } catch (ParseException e) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("ilookup", opts);
      throw e;
    }

    final int poolSize = line.hasOption("poolSize") ? Integer.parseInt(line.getOptionValue("poolSize")) : 1;
    final int maxSize = line.hasOption("maxSize") ? Integer.parseInt(line.getOptionValue("maxSize")) : 1;
    final long executions =
        line.hasOption("executions") ? Long.parseLong(line.getOptionValue("executions")) : Long.MAX_VALUE;
    final int delayOnReject =
        line.hasOption("delayOnReject") ? Integer.parseInt(line.getOptionValue("delayOnReject")) : 1000;
    final String[] hosts = line.getOptionValues("host");

    // Disable caches for quicker reproduction
    Security.setProperty("networkaddress.cache.ttl", "0");
    Security.setProperty("networkaddress.cache.negative.ttl", "0");

    LinkedBlockingQueue<Runnable> q = new LinkedBlockingQueue<>(maxSize);
    ExecutorService executor = new ThreadPoolExecutor(poolSize, maxSize, 0, TimeUnit.MILLISECONDS, q);
    for (int i = 0; i < executions; i++) {
      try {
        executor.submit(() -> {
          for (String h : hosts) {
            try {
              InetAddress addr = InetAddress.getByName(h);
              String hostName = addr.getCanonicalHostName();
              System.out.println("Address: " + addr);
              System.out.println("Canonical host: " + hostName);
            } catch (UnknownHostException e) {
              e.printStackTrace();
            }
          }
        });
      } catch (RejectedExecutionException e) {
        // Wait till the threadpool reaches the core size
        while (q.size() > poolSize) {
          Thread.sleep(delayOnReject);
        }
      }
    }
    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.MINUTES);
  }
}
