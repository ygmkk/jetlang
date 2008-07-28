package org.jetlang.core;

/// <summary>
/// Default command executor.

/// </summary>
public class RunnableInvokerImpl implements RunnableInvoker {

    public boolean isRunning() {
        return _running;
    }

    public void setRunning(boolean _running) {
        this._running = _running;
    }

    private boolean _running = true;

    /// <summary>
    /// <see cref="RunnableInvoker.ExecuteAll(Command[])"/>
    /// </summary>
    /// <param name="toExecute"></param>
    public void executeAll(Runnable[] toExecute) {
        for (Runnable command : toExecute) {
            if (_running) {
                command.run();
            }
        }
    }

}