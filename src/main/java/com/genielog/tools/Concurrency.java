package com.genielog.tools;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Awaitility;

import com.genielog.tools.functional.SerializableConsumer;
import com.genielog.tools.functional.SerializablePredicate;

public class Concurrency implements Closeable {

	Logger logger = LogManager.getLogger(Concurrency.class);
	public ExecutorService executor = null;
	int startDelay = 0;

	public static final Concurrency instance = new Concurrency();

	public Concurrency() {
		this(Integer.max(2, Runtime.getRuntime().availableProcessors() - 2));
	}

	public Concurrency(int nbThreads) {
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

	//
	// ****************************************************************************************************************
	//

	/**
	 * Execute a concurrent Map operation on a sequence of source items provided in a list. The provided list will be
	 * virtually split into consecutive sequences of chunkSize length and each sequence will be processed in a dedicated
	 * thread with the Map operator.
	 * 
	 * The execution of this method is synchronous and will return when each list provided will be processed.
	 * 
	 * @param <SOURCE>
	 *          The type of the items in the list.
	 * @param source
	 *          The list of items to be processed concurrently
	 * @param mapper
	 *          The operator to apply on each item of the list.
	 */
	public <SOURCE> void forEach(List<SOURCE> source, SerializableConsumer<SOURCE> mapper) {
		int chunkSize = 1000;
		if (source.size() < chunkSize) {
			source.forEach(mapper);
		} else {
			List<Future<?>> futures = new ArrayList<>();

			int index = 0;
			while (index < source.size()) {
				int firstIndex = index;
				int lastIndex = Integer.min(source.size(), index + chunkSize);
				futures.add(CompletableFuture.runAsync(() -> {
					for (int i = firstIndex; i < lastIndex; i++) {
						try {
							mapper.accept(source.get(i));
						} catch (Exception e) {
							logger.error("Concurrent task triggered an exception: {}", e.getLocalizedMessage());
							e.printStackTrace();
						}
					}
				}, executor));
				index += chunkSize;
			}

			while (!futures.isEmpty()) {
				Future<?> f = futures.stream().filter(Future::isDone).findFirst().orElse(null);
				if (f != null) {
					if (listener != null)
						logger.debug("Processing chunk done, {} chunks left", (futures.size() - 1));
					futures.remove(f);
					if (listener != null) {
						try {
							listener.accept(f.get());
						} catch (InterruptedException | ExecutionException e) {
							logger.error("Listener triggered an exception: {}", e.getLocalizedMessage());
						}
					}
				} else {
					Awaitility.await().atLeast(500, TimeUnit.MILLISECONDS);
				}
			}
		}

	}

	//
	// ******************************************************************************************************************
	//

	/**
	 * From a source of items provided with a stream, search for the first one matching the finder predicate. As soon as a
	 * match is found, all threads are interrupted.
	 * 
	 * @param <SOURCE>
	 * @param source
	 * @param finder
	 * @return
	 */
	public <SOURCE> SOURCE search(Stream<SOURCE> source, SerializablePredicate<SOURCE> finder) {

		MapRedOperator<SOURCE, SOURCE> searchOperator = new MapRedOperator<>();
		searchOperator.filter = finder;
		searchOperator.mapper = item -> item;
		searchOperator.reducer = (prev, contrib) -> {
			if (contrib != null) {
				abort();
			}
			return contrib;
		};

		searchOperator.initValueSupplier = () -> null;

		return parallel(source, 1000, searchOperator);

	}

	public <SOURCE> void parallel(Stream<SOURCE> sources, int chunkSize, SerializableConsumer<SOURCE> action) {
		Spliterator<SOURCE> splitSources = sources.spliterator();
		List<Future<?>> futures = new ArrayList<>();
		int nbChunks = 0;
		aborted = false;
		while (!aborted) {

			//
			// Create the chunk of source data to be processed by a same process
			//
			List<SOURCE> chunk = new ArrayList<>(chunkSize);
			for (int i = 0; (!aborted) && (i < chunkSize) && splitSources.tryAdvance(chunk::add); i++)
				;
			if (chunk.isEmpty())
				break;

			nbChunks++;
			//
			// Launching a new process for the source data.
			//
			if (listener != null)
				logger.debug("Starting a new chunk for {} entries", chunk.size());
			futures.add(CompletableFuture.supplyAsync(() -> {
				for (int iSrc = 0; (!aborted) && (iSrc < chunk.size()); iSrc++) {
					try {
						SOURCE src = chunk.get(iSrc);
						action.accept(src);
					} catch (Exception e) {
						logger.error("Concurrent task triggered an exception: {}", e.getLocalizedMessage());
						e.printStackTrace();
					}
				}
				return chunk;
			}, executor));

			Awaitility.await().atLeast(startDelay, TimeUnit.MILLISECONDS);
		}

		if (listener != null)
			logger.info("{} Chunks of {} size submitted.", nbChunks, chunkSize);

		while (!futures.isEmpty()) {
			Future<?> f = futures.stream().filter(Future::isDone).findFirst().orElse(null);
			if (f != null) {
				if (listener != null)
					logger.debug("Processing chunk done, {} chunks left", (futures.size() - 1));
				futures.remove(f);
				if (listener != null) {
					try {
						listener.accept(f.get());
					} catch (InterruptedException | ExecutionException e) {
						logger.error("Listener triggered an exception: {}", e.getLocalizedMessage());
					}
				}
			} else {
				Awaitility.await().atLeast(500, TimeUnit.MILLISECONDS);
			}
		}
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
				return operator.exec(chunk.stream());
			}, executor));

			try {
				Thread.sleep(startDelay);
			} catch (InterruptedException e) {

			}

		}

		//logger.debug("Triggered {} concurrent tasks...", mapFutures.size());

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
				//logger.debug("There are still not enough results ({}) to proceed with a parallel reduction.",
				//		allResults.size());
				if (mapFutures.isEmpty()) {
					operator.result = allResults.stream().reduce(operator.result, operator.reducer);
					allResults.clear();
				}
			} else {
				//logger.debug("Can't execute a parallel reduction on {} results if reducer modify its result", allResults.size());
				operator.result = allResults.stream().reduce(operator.result, operator.reducer);
				allResults.clear();
				//RESULT contrib =   allResults.parallelStream().reduce(operator.initValueSupplier.get(), operator.reducer);
				//operator.result =  operator.reducer.apply(operator.result, contrib);
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
