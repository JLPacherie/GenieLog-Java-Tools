package com.genielog.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class MapRedTests extends BaseTest {

  public List<Integer> makeSequence(int size) {
    return IntStream.iterate(1, x -> x + 1).limit(size).boxed().collect(Collectors.toList());
  }

  MapRedOperator<Integer, Long> intSumOperator = new MapRedOperator<>(null, (Integer x) -> Long.valueOf(x),
      (Long prev, Long contrib) -> {
        prev = prev + contrib;
        return prev;
      }, () -> Long.valueOf(0));

  MapRedOperator<Integer, Long> longOperator = new MapRedOperator<>(
      null, (Integer x) -> {
        Awaitility.await().atLeast(500, TimeUnit.MILLISECONDS);
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

    Concurrency executor = new Concurrency();

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

    Concurrency executor = new Concurrency();

    for (int size : allSizes) {

      List<Integer> list = makeSequence(size);
      long chrono = 0;
      long result = 0;
      
      // Sequential test.
      chrono = System.currentTimeMillis();
      result = executor.parallel(list.stream(), 10000, longOperator);
      long sequentialChrono = System.currentTimeMillis() - chrono;
      assertEquals(result, (long) size * (size + 1) / 2, "Sequential test failed for size " + size);
      _logger.info("Test succeeded for size {}, {} item/msec", size, (sequentialChrono > 0) ? size / sequentialChrono : "Inf.");

      // MT test.
      chrono = System.currentTimeMillis();
      result = executor.parallel(list.stream(), 10000, longOperator);
      long mtChrono = System.currentTimeMillis() - chrono;
      assertEquals(result, (long) size * (size + 1) / 2, "MT test failed for size " + size);
      _logger.info("Test succeeded for size {}, {} item/msec", size, (mtChrono > 0) ? size / mtChrono : "Inf.");

      _logger.info("Test succeeded for size {}, MT perfs adds  up to {} %", size, (sequentialChrono > 0) ? ( 100 * (double) mtChrono / sequentialChrono) : "0");

    }
  }
}
