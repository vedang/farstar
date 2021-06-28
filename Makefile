.PHONY: test

compile:
	@echo "\nRecompiling Clojure files"
	lein do clean, deps, compile

start:
	@echo "\nStarting a Clojure Repl"
	lein trampoline repl :headless

clj: compile start

test: compile
	@echo "\nRunning all tests"
	lein test

clean:
	lein clean

check:
	@echo "\nRunning clj-kondo"
	clj-kondo --lint .

style:
	@echo "\nRunning cljstyle"
	cljstyle check .
