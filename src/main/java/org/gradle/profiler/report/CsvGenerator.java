package org.gradle.profiler.report;

import com.kstruct.gethostname4j.Hostname;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.gradle.internal.impldep.org.apache.commons.lang.StringUtils;
import org.gradle.profiler.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CsvGenerator extends AbstractGenerator {

    public CsvGenerator(File outputFile) {
        super(outputFile);
    }

    @Override
    protected void write(BenchmarkResult benchmarkResult, BufferedWriter writer) throws IOException {
        String hostname = "";
        try {
            hostname = Hostname.getHostname();
        } catch (RuntimeException e) {
            System.err.println("Error obtaining hostname: " + e.getLocalizedMessage());
        }
        List<? extends BuildScenarioResult> allScenarios = benchmarkResult.getScenarios();
        writer.write("scenario");
        for (BuildScenarioResult result : allScenarios) {
            writer.write(",");
            writer.write(result.getScenarioDefinition().getName());
        }
        writer.newLine();
        writer.write("build tool");
        for (BuildScenarioResult result : allScenarios) {
            writer.write(",");
            writer.write(result.getScenarioDefinition().getBuildTool());
        }
        writer.newLine();
        writer.write("version");
        for (BuildScenarioResult result : allScenarios) {
            writer.write(",");
            writer.write(result.getScenarioDefinition().getBuildToolVersion());
        }
        writer.newLine();
        writer.write("hostname");
        for (int i = 0; i < allScenarios.size(); i++) {
            writer.write(",");
            writer.write(hostname);
        }
        writer.newLine();
        writer.write("source");
        for (int i = 0; i < allScenarios.size(); i++) {
            writer.write(",");
            writer.write(getOutputFile().getCanonicalPath());
        }
        writer.newLine();
        writer.write("tasks");
        for (BuildScenarioResult result : allScenarios) {
            writer.write(",");
            writer.write(result.getScenarioDefinition().getTasksDisplayName());
        }
        writer.newLine();
        writer.write("commands");
        for (BuildScenarioResult result : allScenarios) {
            ScenarioDefinition scenarioDefinition = result.getScenarioDefinition();
            writer.write(",");
            if (scenarioDefinition instanceof BazelScenarioDefinition) {
                List<String> commands = ((BazelScenarioDefinition) scenarioDefinition).getCommands();
                writer.write(StringUtils.join(commands, " "));
            } else if (scenarioDefinition instanceof GradleScenarioDefinition) {
                List<String> commands = ((GradleScenarioDefinition) scenarioDefinition).getGradleArgs();
                writer.write(StringUtils.join(commands, " "));
            } else {
                writer.write("");
            }
        }
        writer.newLine();

        int maxRows = allScenarios.stream().mapToInt(v -> v.getResults().size()).max().orElse(0);
        for (int row = 0; row < maxRows; row++) {
            for (BuildScenarioResult result : allScenarios) {
                List<? extends BuildInvocationResult> results = result.getResults();
                if (row >= results.size()) {
                    continue;
                }
                BuildInvocationResult buildResult = results.get(row);
                writer.write(buildResult.getDisplayName());
                break;
            }
            for (BuildScenarioResult result : allScenarios) {
                List<? extends BuildInvocationResult> results = result.getResults();
                writer.write(",");
                if (row >= results.size()) {
                    continue;
                }
                BuildInvocationResult buildResult = results.get(row);
                writer.write(String.valueOf(buildResult.getExecutionTime().toMillis()));
            }
            writer.newLine();
        }

        List<DescriptiveStatistics> statistics = allScenarios.stream().map(BuildScenarioResult::getStatistics).collect(Collectors.toList());
        statistic(writer, "mean", statistics, DescriptiveStatistics::getMean);
        statistic(writer, "min", statistics, DescriptiveStatistics::getMin);
        statistic(writer, "25th percentile", statistics, v -> v.getPercentile(25));
        statistic(writer, "median", statistics, v -> v.getPercentile(50));
        statistic(writer, "75th percentile", statistics, v -> v.getPercentile(75));
        statistic(writer, "max", statistics, DescriptiveStatistics::getMax);
        statistic(writer, "stddev", statistics, DescriptiveStatistics::getStandardDeviation);
    }

    private void statistic(BufferedWriter writer, String name, List<DescriptiveStatistics> statistics, Function<DescriptiveStatistics, Double> value) throws IOException {
        writer.write(name);
        for (DescriptiveStatistics statistic : statistics) {
            writer.write(",");
            writer.write(String.valueOf(value.apply(statistic)));
        }
        writer.newLine();
    }
}
