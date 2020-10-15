package com.genielog.tools;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.genielog.tools.functional.SerializableBinaryOperator;
import com.genielog.tools.functional.SerializableConsumer;
import com.genielog.tools.functional.SerializableFunction;
import com.genielog.tools.functional.SerializablePredicate;
import com.genielog.tools.functional.SerializableSupplier;

public class MapRedOperator<ITEM, RESULT> implements Serializable {

	protected Logger logger = null;

	private static final long serialVersionUID = 6416474010519151325L;

	public String id;
	// The filter is used to control on which items the operator will actually be executed.
	public SerializablePredicate<ITEM> filter;

	// The mapper defines how result items are generated from source items.
	public SerializableFunction<ITEM, RESULT> mapper;

	// The reducer defines how two results can be combined to produce a final result
	public SerializableBinaryOperator<RESULT> reducer;

	public SerializableSupplier<RESULT> initValueSupplier;

	public RESULT result;

	/** Build an operator to search for the first item in a sequence matching the given predicate. */
	public static <ITEM> MapRedOperator<ITEM, ITEM> findAny(SerializablePredicate<ITEM> finder) {
		MapRedOperator<ITEM, ITEM> result = new MapRedOperator<>("findAny");
		result.filter = (ITEM item) -> finder.test(item);
		result.mapper = (ITEM item) -> item;
		result.reducer = (ITEM prev, ITEM contrib) -> {
			result.abort();
			return contrib;
		};
		result.initValueSupplier = () -> null;
		return result;
	}

	/** Build an operator to search for all first item in a sequence matching the given predicate. */
	public static <ITEM> MapRedOperator<ITEM, List<ITEM>> findAll(SerializablePredicate<ITEM> finder) {
		MapRedOperator<ITEM, List<ITEM>> result = new MapRedOperator<>("findAll");
		result.filter = (ITEM item) -> finder.test(item);
		result.mapper = (ITEM item) -> List.of(item);
		result.reducer = (List<ITEM> prev, List<ITEM> contrib) -> {
			prev.addAll(contrib);
			return prev;
		};
		result.initValueSupplier = () -> new ArrayList<>();
		return result;
	}

	/** Build a Map operator that wull return the number of unfiltered items processed with the mapper. */
	public static <ITEM> MapRedOperator<ITEM, Integer> forEach(	String id,
																															SerializablePredicate<ITEM> filter,
																															SerializableConsumer<ITEM> mapper) {
		return new MapRedOperator<>(id, filter,
				(ITEM item) -> {
					mapper.accept(item);
					return 1;
				},
				(Integer prev, Integer contrib) -> prev + contrib,
				() -> Integer.valueOf(0));

	}

	public MapRedOperator(String id) {
		logger = LogManager.getLogger(this.getClass());
		this.id = id;
	}

	public MapRedOperator(String id,
		SerializablePredicate<ITEM> filter,
		SerializableFunction<ITEM, RESULT> mapper,
		SerializableBinaryOperator<RESULT> reducer,
		SerializableSupplier<RESULT> initValueSupplier) {
		this(id);
		this.filter = filter;
		this.mapper = mapper;
		this.reducer = reducer;
		this.initValueSupplier = initValueSupplier;
	}

	//
	//
	//
	public void init() {
		result = initValueSupplier.get();
		isAborted = false;
	}

	private volatile boolean isAborted = false;

	public boolean isAborted() {
		return isAborted;
	}

	public void abort() {
		//logger.debug("{} Execution aborted.", id);
		isAborted = true;
	}

	/**
	 * Execute the operator on a sequence of source items and generate the result for this source. This method aims at
	 * being executed in each concurrent thread.
	 * 
	 */
	public RESULT exec(Stream<ITEM> t) {
		//logger.debug("{} Starting the operator on local contribution.", id);
		RESULT r = initValueSupplier.get();
		return t.takeWhile(item -> !isAborted).filter(item -> filter == null || filter.test(item)).map(mapper).reduce(r,
				reducer);
	}

}
