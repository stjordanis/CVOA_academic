package entrypoint;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import core.CVOA;
import core.CVOAUtilities;
import core.Individual;
import fitness.FitnessFunction;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class CvoaCLI implements Callable<Integer> {

	// Common parameters
	@Parameters(index = "0", description = "Number of bits", defaultValue = "10")
	private int bits;

	@Option(names = { "-str", "-strain" }, description = "Define a CVOA strain")
	private List<String> strains = new ArrayList<String>();

	@Option(names = { "-f",
	"--fitnessFunction" }, description = "Fitness fucntion name", defaultValue = "fitness.Xminus15")
	private String fitnessFunction;

	@Option(names = { "-ep", "--externalPath" }, description = "External fitness function folder path")
	private File externalFitnessPath;

	@Option(names = { "-mt", "--maxThreads" }, description = "Max threads for concurrent execution", defaultValue = "1")
	private int maxThreads;

	public static final int DEFAULT_STRAIN_ITERATIONS = 20;
	public static final String DEFAULT_STRAIN_ID = "Strain 1";
	public static final int DEFAULT_STRAIN_SEED = 200;

	public static void main(String[] args) {
		int exitCode = new CommandLine(new CvoaCLI()).execute(args);
		System.exit(exitCode);
	}

	@Override
	public Integer call() throws Exception {

		CVOA.initializePandemic(Individual.getExtremeIndividual(false), buildFitnessFunction());

		Collection<CVOA> concurrentCVOAs = new ArrayList<CVOA>(strains.size());

		if (strains.size() == 0)
			concurrentCVOAs.add(new CVOA(bits, DEFAULT_STRAIN_ITERATIONS, DEFAULT_STRAIN_ID, DEFAULT_STRAIN_SEED));
		else {
		
			if (maxThreads < strains.size()) maxThreads = strains.size();
			
			for (String str : strains)
				concurrentCVOAs.add(buidCVOA(str));
		
		}
		ExecutorService pool = Executors.newFixedThreadPool(maxThreads);

		List<Future<Individual>> results = new LinkedList<Future<Individual>>();

		long time = System.currentTimeMillis();

		results = pool.invokeAll(concurrentCVOAs);
		int i = 1;

		pool.shutdown();

		System.out.println("\n************** BEST RESULTS BY STRAIN **************");
		for (Future<Individual> r : results) {
			System.out.println("[Strain #" + i + "] Best solution = " + r.get());
			i++;
		}

		time = System.currentTimeMillis() - time;

		System.out.println("\n************** BEST RESULT **************");
		System.out.println("Best individual: " + Arrays.toString(CVOA.bestSolution.getData()));
		System.out.println("Best fitness: " + CVOA.bestSolution.getFitness());

		System.out.println("\n************** PERFORMANCE **************");
		System.out.println("Execution time: "
				+ (CVOAUtilities.getInstance()).getDecimalFormat("#.##").format(((double) time) / 60000) + " mins");
		System.out.println("\nTotal space explored = " + CVOAUtilities.getInstance().getDecimalFormat("#.##")
				.format((double) 100 * (CVOA.deaths.size() + CVOA.recovered.size()) / Math.pow(2, bits)) + "%");
		System.out.println("\tRecovered: " + CVOA.recovered.size());
		System.out.println("\tDeaths: " + CVOA.deaths.size());
		System.out.println("\tIsolated: " + CVOA.isolated.size());
		System.out.println("\tSearch space : " + (int) Math.pow(2, bits));

		return new Integer(0);
	}

	private CVOA buidCVOA(String strain) throws WrongStrainException {

		CVOA res = null;

		String[] parameters = strain.split(";");

		if (parameters.length == 3) {

			res = new CVOA(bits, Integer.parseInt(parameters[0]), parameters[1], Integer.parseInt(parameters[2]));

		}

		else if (parameters.length != 13) {

			res = new CVOA(bits, Integer.parseInt(parameters[0]), parameters[1],Integer.parseInt(parameters[2]), 
					Integer.parseInt(parameters[3]), Integer.parseInt(parameters[4]), 
					Integer.parseInt(parameters[5]), Integer.parseInt(parameters[6]), 
				    Double.parseDouble(parameters[7]),Double.parseDouble(parameters[8]), 
				    Double.parseDouble(parameters[9]),Double.parseDouble(parameters[10]), 
				    Double.parseDouble(parameters[11]));

		} else {

			throw new WrongStrainException("Wrong strain parameters: " + strain);

		}

		return res;
	}

	private FitnessFunction buildFitnessFunction() {

		FitnessFunction res = null;

		try {

			if (externalFitnessPath == null)
				res = (FitnessFunction) Class.forName(fitnessFunction).newInstance();
			else {
				URL url = externalFitnessPath.toURI().toURL();
				URL[] urls = new URL[] { url };
				@SuppressWarnings("resource")
				Class<?> cls = new URLClassLoader(urls).loadClass(fitnessFunction);
				res = (FitnessFunction) cls.newInstance();
			}

		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException | MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return res;

	}

	private class WrongStrainException extends Exception {

		private static final long serialVersionUID = 8250480763187369474L;

		public WrongStrainException(String msg) {
			super(msg);
		}
	}

}
