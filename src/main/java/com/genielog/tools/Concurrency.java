package com.genielog.tools;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.genielog.tools.functional.SerializableConsumer;

public class Concurrency {

	protected Logger logger = LogManager.getLogger(Concurrency.class);
	protected ThreadFactory namedThreadFactory;
	protected int startDelay = 0;
	protected String name;
	
	// Max delay between two consecutives thread termination
	protected long executionTimeoutMilliSec = 5 * 60 * 1000L; // 1 min
	
	private Runnable beforeEachParallelExecution;
	private Runnable afterEachParallelExecution;
	private int nbThreads = -1;
	
	public Concurrency(String name, int nbThreads) {
		this.name = name;
		this.beforeEachParallelExecution = null;
		this.afterEachParallelExecution = null;
		this.nbThreads = nbThreads;
	}

	public void setStartDelay(int delay) {
		startDelay = delay;
	}

	// This is the listener that, when defined, will be triggered after each mapper execution
	SerializableConsumer<Object> listener = null;

	public void setMonitorListener(SerializableConsumer<Object> listener) {
		this.listener = listener;
	}

	public void onBeforeExecution(Runnable action) {
		this.beforeEachParallelExecution = action;
	}

	public void onAfterExecution(Runnable action) {
		this.afterEachParallelExecution = action;
	}

	// ******************************************************************************************************************
	// Parallel Map + Sequential Reduce
	// ******************************************************************************************************************

	public <RESULT> Callable<RESULT> buildWork(Supplier<RESULT> supplier) {
		return new Callable<>() {
			@Override
			public RESULT call() throws Exception {
				return supplier.get();
			}
		};
	}

	/** Executes an operator on the given source item. The Map part is executed in parallel. */
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

		if ( nbThreads <= 0) {
			nbThreads = Integer.max(2, Runtime.getRuntime().availableProcessors() - 2);
		}
		
		ExecutorService executor = Executors.newFixedThreadPool(nbThreads);

		operator.init();

		Spliterator<SOURCE> splitSources = sources.spliterator();
		List<Future<RESULT>> mapFutures = new ArrayList<>();
		aborted = false;

		if (beforeEachParallelExecution != null)
			beforeEachParallelExecution.run();

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

			Callable<RESULT> work = buildWork(() -> {
				Thread.currentThread().setName(name + "-" + operator.id + "-" + mapFutures.size());
				RESULT result = operator.exec(chunk.stream());
				Thread.currentThread().setName(name + "-waiting-" + mapFutures.size());
				return result;
			});

			Future<RESULT> future = executor.submit(work);

			mapFutures.add(future);

			try {
				Thread.sleep(startDelay);
			} catch (InterruptedException e) {

			}

		}

		// logger.debug("Triggered {} concurrent tasks...", mapFutures.size());

		//
		// Reduction
		//

		RESULT result = operator.initValueSupplier.get();

		long delay = System.currentTimeMillis();

		//
		// While all theads are not terminated, or the execution aborted.
		//
		while (!mapFutures.isEmpty() && !aborted() && !operator.isAborted()) {

			// Fetch all available results from a thread.
			Future<RESULT> future = mapFutures.stream().filter(Future::isDone).findFirst().orElse(null);

			if (future != null) {
				try {
					result = operator.reducer.apply(result, future.get());
				} catch (InterruptedException | ExecutionException e) {
					logger.error(" Concurrent execution aborted, because {}", e.getLocalizedMessage());
					abort();
					e.printStackTrace();
				}
				mapFutures.remove(future);
				future = mapFutures.stream().filter(Future::isDone).findFirst().orElse(null);

				// Reset
				delay = System.currentTimeMillis();
			} else {
				//
				// Check that we don't reach the timeout of unavailable result
				//
				long elapsed = System.currentTimeMillis() - delay;
				if (elapsed >= executionTimeoutMilliSec) {
					logger.error("{}, Aborting because of timed out after {} mins", operator.id, elapsed / (1000 * 60));
					operator.abort();
					abort();
					// Reset current results
					result = operator.initValueSupplier.get();
				} else {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

		}

		// Force shuting down in case of abort
		executor.shutdownNow();

		if (afterEachParallelExecution != null)
			afterEachParallelExecution.run();


		if (!mapFutures.isEmpty() && aborted()) {
			logger.warn("*******************************************************");
			logger.warn("Concurrent execution terminated before finished.");
			logger.warn("   Executor : {} ({})", name, aborted ? "aborted" : "not aborted");
			logger.warn("   Operator : {} ({})", operator.id, operator.isAborted() ? "aborted" : "not aborted");
			logger.warn("   Nb not terminated tasks {}", mapFutures.size());
			logger.warn("*******************************************************");
			throw new IllegalStateException("Concurrent execution timed out, waiting for a task for more than " + executionTimeoutMilliSec / 1000 + " secs");
		}

		return result;

	}

	volatile boolean aborted = false;

	public boolean aborted() {
		return aborted;
	}

	public void abort() {
		aborted = true;
	}

}
