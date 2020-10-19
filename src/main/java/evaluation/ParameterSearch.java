package evaluation;

import core.AbstractPlayer;
import evodef.EvoAlg;
import evodef.LandscapeModel;
import evodef.SearchSpace;
import evodef.SolutionEvaluator;
import games.GameType;
import games.dominion.BigMoney;
import games.dominion.DominionForwardModel;
import games.dominion.DominionGameState;
import games.dominion.DominionParameters;
import ntbea.*;
import players.mcts.MCTSParams;
import players.mcts.MCTSSearchSpace;
import utilities.Pair;
import utilities.StatSummary;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static utilities.Utils.getArg;

public class ParameterSearch {

    public static void main(String[] args) {
        List<String> argsList = Arrays.asList(args);
        if (argsList.contains("--help") || argsList.contains("-h")) System.out.println(
                "The first three arguments must be \n" +
                        "\t<filename for searchSpace definition>\n" +
                        "\t<number of NTBEA iterations>\n" +
                        "\t<game type>" +
                        "Then there are a number of optional arguments:\n" +
                        "\tbaseAgent=     The filename for the baseAgent (from which the searchSpace definition deviates)" +
                        "\tevalGames=     The number of games to run with the best predicted setting to estimate its true value (default is 20% of NTBEA iterations)" +
                        "\topponent=      The filename for the agent used as the opponent" +
                        "\tGameParams=    The filename with game params to use" +
                        "\tuseThreeTuples If specified then we use 3-tuples as well as 1-, 2- and N-tuples" +
                        "\tkExplore=      The k to use in NTBEA - defaults to 100.0" +
                        "\thood=          The size of neighbourhood to look at in NTBEA. Default is min(50, |searchSpace|/100)" +
                        "\trepeat=        The number of times NTBEA should be re-run, to find a single best recommendation"
        );

        if (argsList.size() < 3)
            throw new AssertionError("Must specify at least three parameters: searchSpace, NTBEA iterations, game");
        String searchSpaceFile = args[0];
        int iterationsPerRun = Integer.parseInt(args[1]);
        GameType game = GameType.valueOf(args[2]);
        if (game != GameType.Dominion)
            throw new AssertionError("Only Dominion currently supported");
        int repeats = getArg(args, "repeat", 1);
        int evalGames = getArg(args, "evalGames", iterationsPerRun / 5);
        double kExplore = getArg(args, "kExplore", 100.0);

        //TODO: Convert SearchSpace file to be from JSON (once NTBEA code allows that)
        // TODO: Replace default MCTSPArams with the baseParams from command line
        MCTSSearchSpace searchSpace = new MCTSSearchSpace(new MCTSParams(System.currentTimeMillis()), searchSpaceFile);
        int searchSpaceSize = IntStream.range(0, searchSpace.nDims()).reduce(1, (acc, i) -> acc * searchSpace.nValues(i));
        int twoTupleSize = IntStream.range(0, searchSpace.nDims() - 1)
                .map(i -> searchSpace.nValues(i) *
                        IntStream.range(i + 1, searchSpace.nDims())
                                .map(searchSpace::nValues).sum()
                ).sum();
        int threeTupleSize = IntStream.range(0, searchSpace.nDims() - 2)
                .map(i -> searchSpace.nValues(i) *
                        IntStream.range(i + 1, searchSpace.nDims()).map(j ->
                                searchSpace.nValues(j) * IntStream.range(j + 1, searchSpace.nDims()).map(searchSpace::nValues).sum()
                        ).sum()
                ).sum();

        int hood = getArg(args, "hood", Math.min(50, searchSpaceSize / 100));
        boolean useThreeTuples = Arrays.asList(args).contains("useThreeTuples");

        System.out.println(String.format("Search space consists of %d states and %d possible 2-Tuples%s",
                searchSpaceSize, twoTupleSize, useThreeTuples ? String.format(" and %d 3-Tuples", threeTupleSize) : ""));

        for (int i = 0; i < searchSpace.nDims(); i++) {
            int finalI = i;
            String allValues = IntStream.range(0, searchSpace.nValues(i)).mapToObj(j -> searchSpace.value(finalI, j)).map(Object::toString).collect(Collectors.joining(", "));
            System.out.println(String.format("%20s has %d values %s", searchSpace.name(i), searchSpace.nValues(i), allValues));
        }

        NTupleSystem landscapeModel = new NTupleSystem(searchSpace);
        landscapeModel.setUse3Tuple(useThreeTuples);
        landscapeModel.addTuples();

        NTupleBanditEA searchFramework = new NTupleBanditEA(landscapeModel, kExplore, hood);
        DominionParameters params = new DominionParameters(42);
        long seed = 42;
        List<AbstractPlayer> opponents = new ArrayList<>();
        opponents.add(new BigMoney());

        // TODO: Need a game solution evaluator
        SolutionEvaluator evaluator = new GameEvaluator(
                GameType.Dominion,
                searchSpace,
                new DominionForwardModel(),
                new DominionGameState(params, 4),
                4,
                opponents,
                new Random(seed),
                false
        );

        long startTime = System.currentTimeMillis();

        Pair<Double, Double> r = runNTBEA(evaluator, searchFramework, iterationsPerRun, iterationsPerRun, evalGames, false);
        Pair<Pair<Double, Double>, double[]> retValue = new Pair<>(r, landscapeModel.getBestOfSampled());
        printDetailsOfRun(retValue, searchSpace);

        // TODO: Add repeats of main NTBEA and print out the final recommendation
//            System.out.println("\nFinal Recommendation: ");
//            printDetailsOfRun(retValue, searchSpace);
    }


    private static void printDetailsOfRun(Pair<Pair<Double, Double>, double[]> data, MCTSSearchSpace searchSpace) {
        System.out.println(String.format("Recommended settings have score %.3g +/- %.3g:\t%s\n %s",
                data.a.a, data.a.b,
                Arrays.stream(data.b).mapToObj(it -> String.format("%.0f", it)).collect(Collectors.joining(", ")),
                IntStream.range(0, data.b.length).mapToObj(i -> new Pair<>(i, data.b[i]))
                        .map(p -> {
                                    int paramIndex = p.a;
                                    double valueIndex = p.b;
                                    Object value = searchSpace.value(paramIndex, (int) valueIndex);
                                    String valueString = value.toString();
                                    if (value instanceof Integer) {
                                        valueString = String.format("%d", value);
                                    } else if (value instanceof Double) {
                                        valueString = String.format("%.3g", value);
                                    }
                                    return String.format("\t%s:\t%s\n", searchSpace.name(paramIndex), valueString);
                                }
                        ).collect(Collectors.joining(" "))));
    }

    /*
    The final result will be held in searchFramework.landscapeModel.bestOfSample
     */
    public static Pair<Double, Double> runNTBEA(SolutionEvaluator evaluator,
                                                EvoAlg searchFramework,
                                                int totalRuns, int reportEvery,
                                                int evalGames, boolean logResults) {

        NTupleSystem landscapeModel = (NTupleSystem) searchFramework.getModel();
        SearchSpace searchSpace = landscapeModel.getSearchSpace();

        for (int iter = 0; iter < totalRuns / reportEvery; iter++) {
            evaluator.reset();
            searchFramework.runTrial(evaluator, reportEvery);

            if (logResults) {
                System.out.println("Current best sampled point (using mean estimate): " +
                        Arrays.toString(landscapeModel.getBestOfSampled()) +
                        String.format(", %.3g", landscapeModel.getMeanEstimate(landscapeModel.getBestOfSampled())));

                String tuplesExploredBySize = Arrays.toString(IntStream.rangeClosed(1, searchSpace.nDims())
                        .map(size -> landscapeModel.getTuples().stream()
                                .filter(t -> t.tuple.length == size)
                                .mapToInt(it -> it.ntMap.size())
                                .sum()
                        ).toArray());

                System.out.println("Tuples explored by size: " + tuplesExploredBySize);
                System.out.println(String.format("Summary of 1-tuple statistics after %d samples:", landscapeModel.numberOfSamples()));

                IntStream.range(0, searchSpace.nDims()) // assumes that the first N tuples are the 1-dimensional ones
                        .mapToObj(i -> new Pair<>(searchSpace.name(i), landscapeModel.getTuples().get(i)))
                        .forEach(nameTuplePair ->
                                nameTuplePair.b.ntMap.keySet().stream().sorted().forEach(k -> {
                                    StatSummary v = nameTuplePair.b.ntMap.get(k);
                                    System.out.println(String.format("\t%20s\t%s\t%d trials\t mean %.3g +/- %.2g", nameTuplePair.a, k, v.n(), v.mean(), v.stdErr()));
                                })
                        );

                System.out.println("\nSummary of 10 most tried full-tuple statistics:");
                landscapeModel.getTuples().stream()
                        .filter(t -> t.tuple.length == searchSpace.nDims())
                        .forEach(t -> t.ntMap.keySet().stream()
                                .map(k -> new Pair<>(k, t.ntMap.get(k)))
                                .sorted(Comparator.comparing(p -> -p.b.n()))
                                .limit(10)
                                .forEach(item ->
                                        System.out.println(String.format("\t%s\t%d trials\t mean %.3g +/- %.2g\t(NTuple estimate: %.3g)",
                                                item.a, item.b.n(), item.b.mean(), item.b.stdErr(), landscapeModel.getMeanEstimate(item.a.v)))
                                )
                        );
            }
        }
        if (evalGames > 0) {
            double[] results = IntStream.range(0, evalGames)
                    .mapToDouble(answer -> {
                        int[] settings = Arrays.stream(landscapeModel.getBestOfSampled())
                                .mapToInt(d -> (int) d)
                                .toArray();
                        return evaluator.evaluate(settings);
                    }).toArray();

            double avg = Arrays.stream(results).average().getAsDouble();
            double stdErr = Math.sqrt(Arrays.stream(results)
                    .map(d -> Math.pow(d - avg, 2.0)).sum()
                    / (evalGames - 1.0));

            return new Pair<>(avg, stdErr);
        } else {
            return new Pair<>(landscapeModel.getMeanEstimate(landscapeModel.getBestOfSampled()), 0.0);
        }
    }

}