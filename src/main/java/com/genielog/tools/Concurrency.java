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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Awaitility;

import com.genielog.tools.functional.SerializableConsumer;
import com.genielog.tools.functional.SerializableFunction;
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

	SerializableConsumer<Object> listener = null;

	public void setMonitorListener(SerializableConsumer<Object> listener) {
		this.listener = listener;
	}

	public <SOURCE> void forEach(List<SOURCE> source, SerializableConsumer<SOURCE> mapper) {
		int chunkSize = 1000;
		if (source.size() < chunkSize) {
			source.forEach(mapper);
		} else {
			List<Future> futures = new ArrayList<>();

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
				Future f = futures.stream().filter(Future::isDone).findFirst().orElse(null);
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

	// ******************************************************************************************************************
	// Parallel Map
	// ******************************************************************************************************************

	public <SOURCE> SOURCE search(Stream<SOURCE> source, SerializablePredicate<SOURCE> finder) {

		AtomicReference<SOURCE> result = new AtomicReference<>(null);

		parallel(source, 1000,
				(SOURCE item) -> finder.test(item) ? item : null,
				(SOURCE match) -> {
					if (match != null) {
						result.set(match);
						aborted = true;
					}
				});

		return result.get();
	}

	public <SOURCE> void parallel(Stream<SOURCE> sources, int chunkSize, SerializableConsumer<SOURCE> action) {
		Spliterator<SOURCE> splitSources = sources.spliterator();
		List<Future> futures = new ArrayList<>();
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
			Future f = futures.stream().filter(Future::isDone).findFirst().orElse(null);
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
	public <SOURCE, DEST> void parallel(
																			Stream<SOURCE> sources,
																			int chunkSize,
																			SerializableFunction<SOURCE, DEST> mapAction,
																			SerializableConsumer<DEST> reduceAction)
	//
	{

		Spliterator<SOURCE> splitSources = sources.spliterator();
		List<Future<List<DEST>>> mapFutures = new ArrayList<>();
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
				List<DEST> chunkResults = new ArrayList<>();
				for (int iSrc = 0; (!aborted) && (iSrc < chunk.size()); iSrc++) {
					try {
						SOURCE src = chunk.get(iSrc);
						chunkResults.add(mapAction.apply(src));
					} catch (Exception e) {
						logger.error("Concurrent task triggered an exception: {}", e.getLocalizedMessage());
						e.printStackTrace();
					}
				}
				return chunkResults;
			}, executor));

			try {
				Thread.sleep(startDelay);
			} catch (InterruptedException e) {

			}

		}

		//
		// Reduction
		//
		while (!mapFutures.isEmpty()) {
			Future<List<DEST>> f = mapFutures.stream().filter(Future::isDone).findFirst().orElse(null);

			while (f != null) {

				List<DEST> contrib = null;
				try {
					contrib = f.get();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}

				if (listener != null) {
					listener.accept(contrib);
				}

				if (contrib != null) {
					if (listener != null)
						logger.debug("Processing new results chunk for {} values, {} chunks left", contrib.size(),
								(mapFutures.size() - 1));
					//
					// Processing each result
					//
					if (reduceAction != null) {
						for (DEST result : contrib) {
							try {
								reduceAction.accept(result);
							} catch (Exception e) {
								logger.error("Reducing task triggered an exception: {}", e.getLocalizedMessage());
							}
						}
					}
					if (listener != null) {
						logger.debug("Processing done, {} chunks left", (mapFutures.size() - 1));
					}
				}
				mapFutures.remove(f);
				f = mapFutures.stream().filter(Future::isDone).findFirst().orElse(null);
			}
			Awaitility.await().atLeast(500, TimeUnit.MILLISECONDS);
		}
	}

	volatile boolean aborted = false;

	public boolean aborted() {
		return aborted;
	}
	public void abort() {
		aborted = true;
	}

}
