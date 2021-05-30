package com.genielog.tools;

import org.apache.commons.lang3.time.DurationFormatUtils;

public class Chrono {

	protected boolean _isRunning;
	protected long _lastTime;
	protected long _elapsed;

	public void start() {

		if (_isRunning) {
			throw new IllegalStateException("Chronometer already started.");
		}

		_elapsed = 0L;
		_isRunning = true;
		_lastTime = System.currentTimeMillis();
	}

	public long elapsed() {
		return _elapsed + (_isRunning ? System.currentTimeMillis() - _lastTime : 0L);
	}

	/** Stop the chrono, returning the current elapsed time. */
	public long stop() {
		if (!_isRunning) {
			throw new IllegalStateException("Chronometer not started.");
		}

		_elapsed += System.currentTimeMillis() - _lastTime;
		return _elapsed;
	}

	/** Pause the chrono, returning the current elapsed time. */
	public long pause() {
		if (!_isRunning) {
			throw new IllegalStateException("Chronometer not started.");
		}
		_elapsed += System.currentTimeMillis() - _lastTime;
		_isRunning = false;
		return _elapsed;
	}

	/** Continue a paused status. */
	public void resume() {
		if (_isRunning) {
			throw new IllegalStateException("Chronometer already running.");
		}
		_lastTime = System.currentTimeMillis();
		_isRunning = true;
	}

	public static String getStrDuration(long duration) {
		return DurationFormatUtils.formatDuration(duration, "HH:mm:ss,SSS");
	}
	
	public String toString() {
		return getStrDuration(elapsed());
	}
}
