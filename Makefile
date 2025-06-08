.PHONY: build publish

build:
	./gradlew build

publish:
	./gradlew build	
	./gradlew publish