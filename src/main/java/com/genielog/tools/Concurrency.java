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
import com.genielog.tools.functional.SerializableFunction;

public class Concurrency implements Closeable {

	Logger logger = LogManager.getLogger(Concurrency.class);
	ExecutorService executor = null;
	
	public Concurrency() {
		executor =  Executors.newFixedThreadPool(10);
	}
	
	@Override
	public void close() {
		executor.shutdown();
	}

	//******************************************************************************************************************
	// Parallel Map
	// ******************************************************************************************************************
	
	public <SOURCE> void parallel(Stream<SOURCE> sources, int chunkSize, SerializableConsumer<SOURCE> action) {
		Spliterator<SOURCE> splitSources = sources.spliterator();
		List<Future> futures = new ArrayList<>();
		
		while (true) {

			//
			// Create the chunk of source data to be processed by a same process
			//
			List<SOURCE> chunk = new ArrayList<>(chunkSize);
			for (int i = 0; i < chunkSize && splitSources.tryAdvance(chunk::add); i++)
				;
			if (chunk.isEmpty())
				break;

			//
			// Launching a new process for the source data.
			//
			logger.debug("Starting a new chunk for {} entries", chunk.size());
			futures.add(CompletableFuture.runAsync(() -> {
				for (SOURCE src : chunk) {
					action.accept(src);
				}
			}, executor));
		}
		
		while (!futures.isEmpty()) {
			Future f = futures.stream().filter(Future::isDone).findFirst().orElse(null);
			if (f != null) {
					logger.debug("Processing chunk done, {} chunks left", (futures.size() - 1));
			} else {
				Awaitility.await().atLeast(500, TimeUnit.MILLISECONDS);
			}
		}
	}
	
	//******************************************************************************************************************
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
		
		while (true) {

			//
			// Create the chunk of source data to be processed by a same process
			//
			List<SOURCE> chunk = new ArrayList<>(chunkSize);
			for (int i = 0; i < chunkSize && splitSources.tryAdvance(chunk::add); i++)
				;
			if (chunk.isEmpty())
				break;

			//
			// Launching a new process for the source data.
			//
			logger.debug("Starting a new chunk for {} entries", chunk.size());
			mapFutures.add(CompletableFuture.supplyAsync(() -> {
				List<DEST> chunkResults = new ArrayList<>();
				for (SOURCE src : chunk) {
					chunkResults.add(mapAction.apply(src));
				}

				return chunkResults;
			}, executor));
		}

		//
		// Reduction
		//
		while (!mapFutures.isEmpty()) {
			Future<List<DEST>> f = mapFutures.stream().filter(Future::isDone).findFirst().orElse(null);
			if (f != null) {

				List<DEST> contrib = null;
				try {
					contrib = f.get();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
				if (contrib != null) {
					logger.debug("Processing new results chunk for {} values, {} chunks left", contrib.size(),
							(mapFutures.size() - 1));
					//
					// Processing each result
					//
					if (reduceAction != null) {
						for (DEST result : contrib) {
							reduceAction.accept(result);
						}
					}
					logger.debug("Processing done, {} chunks left", (mapFutures.size() - 1));
				}
				mapFutures.remove(f);
			} else {
				Awaitility.await().atLeast(500, TimeUnit.MILLISECONDS);
			}
		}
	}

}
