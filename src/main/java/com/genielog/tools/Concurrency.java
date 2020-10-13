package com.genielog.tools;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.genielog.tools.functional.SerializableConsumer;

public class Concurrency implements Closeable {

	Logger logger = LogManager.getLogger(Concurrency.class);
	public ExecutorService executor = null;
	ThreadFactory namedThreadFactory;
	int startDelay = 0;
	String name;
	public static final Concurrency instance = new Concurrency("shared",
			Integer.max(2, Runtime.getRuntime().availableProcessors() - 2));

	public Concurrency(String name, int nbThreads) {
		this.name = name;
		executor = Executors.newFixedThreadPool(nbThreads);
	}

	public void setStartDelay(int delay) {
		startDelay = delay;
	}

	@Override
	public void close() {
		executor.shutdown();
	}

	// This is the listener that, when defined, will be triggered after each mapper execution
	SerializableConsumer<Object> listener = null;

	public void setMonitorListener(SerializableConsumer<Object> listener) {
		this.listener = listener;
	}

	// ******************************************************************************************************************
	// Parallel Map + Sequential Reduce
	// ******************************************************************************************************************

	public <SOURCE, RESULT> RESULT parallel(Stream<SOURCE> sources,
																					int chunkSize,
																					MapRedOperator<SOURCE, RESULT> operator) {

		if (operator == null) {
			throw new IllegalArgumentException("Concurrent operator not defined.");
		}

		if (operator.mapper == null) {
			throw new IllegalArgumentException("Concurrent mapper of operator not defined.");
		}

		if (operator.reducer == null) {
			throw new IllegalArgumentException("Concurrent reducer of operator not defined.");
		}

		operator.init();

		Spliterator<SOURCE> splitSources = sources.spliterator();
		List<Future<RESULT>> mapFutures = new ArrayList<>();
		aborted = false;

		while (!aborted) {

			//
			// Create the chunk of source data to be processed by a same process
			//
			List<SOURCE> chunk = new ArrayList<>(chunkSize);
			for (int i = 0; (i < chunkSize) && (!aborted) && splitSources.tryAdvance(chunk::add); i++)
				;
			if (chunk.isEmpty())
				break;

			//
			// Launching a new process for the source data.
			//
			if (listener != null)
				logger.debug("Starting a new chunk for {} entries", chunk.size());

			mapFutures.add(CompletableFuture.supplyAsync(() -> {
				Thread.currentThread().setName(name + "-" + operator.id + "-" + mapFutures.size());
				RESULT result = operator.exec(chunk.stream());
				Thread.currentThread().setName(name + "-waiting-" + mapFutures.size());
				return result;
			}, executor));

			try {
				Thread.sleep(startDelay);
			} catch (InterruptedException e) {

			}

		}

		// logger.debug("Triggered {} concurrent tasks...", mapFutures.size());

		//
		// Reduction
		//

		operator.result = operator.initValueSupplier.get();

		// This is the minimum number of result to process in a parallel reduction
		int minNbFutures = 1000;

		// This is the list of results to process in a parallel reduction
		List<RESULT> allResults = new ArrayList<>();

		while (!mapFutures.isEmpty()) {

			while (!mapFutures.isEmpty() && (allResults.size() < minNbFutures)) {
				Future<RESULT> future = mapFutures.stream().filter(Future::isDone).findFirst().orElse(null);
				while (future != null) {
					try {
						allResults.add(future.get());
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (ExecutionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					mapFutures.remove(future);
					future = mapFutures.stream().filter(Future::isDone).findFirst().orElse(null);
				}
			}

			if (allResults.size() < minNbFutures) {
				// logger.debug("There are still not enough results ({}) to proceed with a parallel reduction.",
				// allResults.size());
				if (mapFutures.isEmpty()) {
					operator.result = allResults.stream().reduce(operator.result, operator.reducer);
					allResults.clear();
				}
			} else {
				// logger.debug("Can't execute a parallel reduction on {} results if reducer modify its result",
				// allResults.size());
				operator.result = allResults.stream().reduce(operator.result, operator.reducer);
				allResults.clear();
				// RESULT contrib = allResults.parallelStream().reduce(operator.initValueSupplier.get(), operator.reducer);
				// operator.result = operator.reducer.apply(operator.result, contrib);
			}
			//
			// while (f != null) {
			//
			// RESULT contrib = null;
			// try {
			// contrib = f.get();
			// } catch (InterruptedException | ExecutionException e) {
			// e.printStackTrace();
			// }
			//
			// if (listener != null) {
			// listener.accept(contrib);
			// }
			//
			// if (contrib != null) {
			//
			// if (listener != null)
			// logger.debug("Processing new results chunk for {} values, {} chunks left", chunkSize,
			// (mapFutures.size() - 1));
			//
			// //
			// // Processing each result
			// //
			// if (operator.reducer != null) {
			//
			// try {
			// operator.result = operator.reducer.apply(operator.result, contrib);
			// } catch (Exception e) {
			// logger.error("Reducing task triggered an exception: {}", e.getLocalizedMessage());
			// }
			// }
			// }
			//
			// if (listener != null) {
			// logger.debug("Processing done, {} chunks left", (mapFutures.size() - 1));
			// }
			//
			// mapFutures.remove(f);
			// f = mapFutures.stream().filter(Future::isDone).findFirst().orElse(null);
			// }

			if (!mapFutures.isEmpty()) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		return operator.result;

	}

	volatile boolean aborted = false;

	public boolean aborted() {
		return aborted;
	}

	public void abort() {
		aborted = true;
	}

}
