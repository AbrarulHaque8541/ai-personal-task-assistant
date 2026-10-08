package com.cue.daymark;

/** One checklist item inside a task. Pure data; validation lives in TaskLogic. */
final class Subtask {
    final String id;
    final String title;
    final boolean done;

    Subtask(String id, String title, boolean done) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.done = done;
    }

    Subtask withDone(boolean nextDone) {
        return new Subtask(id, title, nextDone);
    }

    Subtask withTitle(String nextTitle) {
        return new Subtask(id, nextTitle, done);
    }
}
