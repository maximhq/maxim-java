# Maxim SDK

<div style="display: flex; justify-content: center; align-items: center;margin-bottom:20px;">
<img src="https://cdn.getmaxim.ai/third-party/sdk.png">
</div>

This is JVM (written in Kotlin) SDK for enabling Maxim observability. [Maxim](https://www.getmaxim.ai?ref=npm) is an enterprise grade evaluation and observability platform.

## How to integrate

### Install

1. Gradle/Groovy
```groovy
implementation("ai.getmaxim:sdk:1.2.0")
```
2. Maven

```xml

<dependency>
    <groupId>ai.getmaxim</groupId>
    <artifactId>sdk</artifactId>
    <version>1.2.0</version>
</dependency>
```


## Version changelog

### v1.2.0

- Adds support for Test Runs with workflows, prompt versions, prompt chains, and `yieldsOutput` for custom output functions
- Supports local evaluators, platform evaluators and mixed local + platform evaluator configurations
- Adds simulation support for prompt, workflow and local-execution (`yieldsOutput`) modes
- Supports presets, dataset and local data sources, data functions, environment selection, tags, and human evaluation configs

### v1.1.2

- Fixes Java compatibility

### v1.1.0

- Adds support for ToolCalls, Attachments

### v0.1.2

- First public version
