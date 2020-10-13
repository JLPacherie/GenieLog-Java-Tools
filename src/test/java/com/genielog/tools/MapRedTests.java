package com.genielog.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class MapRedTests extends BaseTest {

	public List<Integer> makeSequence(int size) {
		return IntStream.iterate(1, x -> x + 1).limit(size).boxed().collect(Collectors.toList());
	}

	MapRedOperator<Integer, Long> intSumOperator = new MapRedOperator<>(
			"Sum of Integers",
			// No input filtering
			null,
			// No mapper (identity)
			(Integer x) -> Long.valueOf(x),
			// Reducer is where the computation occurs
			(Long prev, Long contrib) -> {
				prev = prev + contrib;
				return prev;
			},
			// Init value
			() -> Long.valueOf(0));

	MapRedOperator<Integer, Long> longOperator = new MapRedOperator<>(
			"Sum of Integers with delay",
			// No filtering
			null,
			(Integer x) -> {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// Awaitility.await().atLeast(1500, TimeUnit.MILLISECONDS);
				return Long.valueOf(x);
			},
			(Long prev, Long contrib) -> {
				prev = prev + contrib;
				return prev;
			}, () -> Long.valueOf(0));

	@Test
	void testSequential() {

		int allSizes[] = new int[] { 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000 };

		for (int size : allSizes) {

			List<Integer> list = makeSequence(size);

			long chrono = System.currentTimeMillis();
			long result = intSumOperator.exec(list.stream());
			chrono = System.currentTimeMillis() - chrono;
			assertEquals(result, (long) size * (size + 1) / 2, "Test failed for size " + size);
			_logger.info("Test succeeded for size {}, {} item/msec", size, (chrono > 0) ? size / chrono : "Inf.");
		}
	}

	@Test
	void testMultiThreaded() {
		int allSizes[] = new int[] { 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000 };

		Concurrency executor = Concurrency.instance;

		for (int size : allSizes) {

			List<Integer> list = makeSequence(size);

			long chrono = System.currentTimeMillis();
			long result = executor.parallel(list.stream(), 10000, intSumOperator);
			chrono = System.currentTimeMillis() - chrono;
			assertEquals(result, (long) size * (size + 1) / 2, "Test failed for size " + size);
			_logger.info("Test succeeded for size {}, {} item/msec", size, (chrono > 0) ? size / chrono : "Inf.");

		}

	}

	@Test
	void testCompare() {
		int allSizes[] = new int[] { 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000 };

		int nbWorkers = 8;

		Concurrency executor = new Concurrency("testComapre",nbWorkers);
		SimpleDateFormat sdf = new SimpleDateFormat("mm:ss.SS");
		MapRedOperator<Integer, Long> operator = intSumOperator;

		// --------------------------------------------------------------------------------------------------------------
		// Calibration of Sequential Performance.
		// --------------------------------------------------------------------------------------------------------------
		_logger.info("--------------------------------------------------------------");
		_logger.info("Calculating sequential performance");
		_logger.info(" ** Creating test data....");

		int seqSize = 1000;
		List<Integer> list = makeSequence(seqSize);
		long chrono = 0;
		long result = 0;

		// --------------------------------------------------------------------------------------------------------------
		// Sequential test.
		// --------------------------------------------------------------------------------------------------------------
		_logger.info(" Start sequential processing....");
		chrono = System.currentTimeMillis();
		result = operator.exec(list.stream());
		long sequentialChrono = System.currentTimeMillis() - chrono;
		assertEquals((long) seqSize * (seqSize + 1) / 2, result, "Sequential test failed for size " + seqSize);

		double seqBandwidth = 1000 * (double) seqSize / (double) sequentialChrono;

		_logger.info("Sequential Test Succeeded in {} at {} item/sec",
				sdf.format(Date.from(Instant.ofEpochMilli(sequentialChrono))),
				(sequentialChrono > 0) ? String.format("%,7.0f", seqBandwidth) : "Inf.");

		for (int size : allSizes) {

			list = makeSequence(size);
			int blockSize = size / nbWorkers;

			_logger.info("--------------------------------------------------------------");
			_logger.info(" ** Test for size {}", String.format("%,9d", size));
			_logger.info(" ** Concurrency level {}", nbWorkers);
			_logger.info(" ** unit Workload     {}", blockSize);

			// --------------------------------------------------------------------------------------------------------------
			// Concurrent test.
			// --------------------------------------------------------------------------------------------------------------
			_logger.info(" Start concurrent processing....");
			chrono = System.currentTimeMillis();
			result = executor.parallel(list.stream(), blockSize, operator);
			long mtChrono = System.currentTimeMillis() - chrono;
			assertEquals((long) size * (size + 1) / 2, result, "Concurrent test failed for size " + size);

			double concurrentBandwidth = 1000 * (double) size / (double) mtChrono;

			double acceleration = concurrentBandwidth / seqBandwidth;
			_logger.info("Concurrent Test Succeeded reaches acceleration {} for {} items in {} at {} item/sec",
					String.format("%,3.1f", acceleration),
					String.format("%,9d", size),
					sdf.format(Date.from(Instant.ofEpochMilli(mtChrono))),
					(mtChrono > 0) ? String.format("%,7.0f", 1000 * (double) size / (double) mtChrono) : "Inf.");

		}

		executor.close();

	}
}
