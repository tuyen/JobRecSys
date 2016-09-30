package recsys.evaluate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.apache.log4j.Logger;

import recsys.algorithms.RecommendationAlgorithm;
import recsys.algorithms.cbf.CB;
import recsys.algorithms.collaborativeFiltering.CollaborativeFiltering;
import recsys.algorithms.hybird.HybirdRecommeder;
import recsys.datapreparer.CollaborativeFilteringDataPreparer;
import uit.se.evaluation.dtos.ScoreDTO;
import uit.se.evaluation.metrics.AveragePrecision;
import uit.se.evaluation.metrics.FMeasure;
import uit.se.evaluation.metrics.NDCG;
import uit.se.evaluation.metrics.Precision;
import uit.se.evaluation.metrics.RMSE;
import uit.se.evaluation.metrics.Recall;
import uit.se.evaluation.metrics.ReciprocalRank;
import uit.se.evaluation.utils.DatasetUtil;
import utils.MysqlDBConnection;

/**
 * Created with IntelliJ IDEA. User: tuynguye Date: 8/12/16 Time: 9:47 AM To
 * change this template use File | Settings | File Templates.
 */
public class Evaluation {
	String evaluationType;
	int evaluationParam;
	String algorithm;
	int topN;
	String inputDir;
	String evaluationDir;
	String taskId;
	Properties config;
	int truthRank = 3;
	long startTime;
	boolean isEstimate = false;
	static Logger log = Logger.getLogger(Evaluation.class.getName());

	public Evaluation(String evalType, int evalParam, String algorithm, String input, String evalDir, String taskId, long startTime) {
		this.algorithm = algorithm;
		this.evaluationParam = evalParam;
		this.evaluationType = evalType;
		this.inputDir = input;
		this.evaluationDir = evalDir;
		this.taskId = taskId;
		this.startTime = startTime;
		this.config = new Properties();
		try {
			config.load(new FileInputStream(evalDir + "config.properties"));
			topN = Integer.valueOf(config.getProperty("topn"));
			truthRank = Integer.valueOf(config.getProperty("relevant.score"));
		} catch (IOException e) {
			e.printStackTrace();
			log.error(e);
		}
	}

	private void percentageSplit() {
		/**
		 * First step: preparing data, split training and testing data set
		 */
		CollaborativeFilteringDataPreparer dataPreparer = new CollaborativeFilteringDataPreparer(inputDir);
		dataPreparer.splitDataSet(evaluationParam, evaluationDir);

		/**
		 * Second step: call Algorithm execute on training data set
		 */
		trainAlgorithm(null);

		/**
		 * Third step: convert result to boolean type
		 */
		HashMap<Integer, List<ScoreDTO>> groundTruth = DatasetUtil.getGroundTruth(evaluationDir + "testing\\Score.txt",
				truthRank);
		HashMap<Integer, List<ScoreDTO>> rankList = DatasetUtil.getRankList(evaluationDir + "result\\Score.txt",
				truthRank);

		/**
		 * Four step: evaluation
		 */
		HashMap<String, Double> evaluationResult = computeEvaluation(rankList, groundTruth);

		/**
		 * Fifth step: write result to DB
		 */
		writeResult(taskId, evaluationResult, true);
	}

	private void customValidation() {
		/**
		 * First step: preparing data, split training and testing data set
		 */
		CollaborativeFilteringDataPreparer dataPreparer = new CollaborativeFilteringDataPreparer(inputDir);
		dataPreparer.copyFileTo(inputDir, evaluationDir + "training\\");

		/**
		 * Second step: call Algorithm execute on training data set
		 */
		trainAlgorithm(null);

		/**
		 * Third step: convert result to boolean type
		 */
		HashMap<Integer, List<ScoreDTO>> groundTruth = DatasetUtil.getGroundTruth(evaluationDir + "testing\\Score.txt",
				truthRank);
		HashMap<Integer, List<ScoreDTO>> rankList = DatasetUtil.getRankList(evaluationDir + "result\\Score.txt",
				truthRank);

		/**
		 * Fourth step: evaluation
		 */
		HashMap<String, Double> evaluationResult = computeEvaluation(rankList, groundTruth);

		/**
		 * Fifth step: write result to DB
		 */
		writeResult(taskId, evaluationResult, true);
	}

	private void crossValidation() {

		CollaborativeFilteringDataPreparer dataPreparer = new CollaborativeFilteringDataPreparer(inputDir);
		HashMap<String, Double> evaluationResult = null;

		for (int i = 0; i < evaluationParam; i++) {
			/**
			 * First step: preparing data, split training and testing data set
			 */
			
			dataPreparer.splitDataSet(i, evaluationParam, inputDir, evaluationDir);

			/**
			 * Second step: call Algorithm execute on training data set
			 */
			trainAlgorithm(null);

			/**
			 * Third step: convert result to boolean type
			 */
			HashMap<Integer, List<ScoreDTO>> groundTruth = DatasetUtil
					.getGroundTruth(evaluationDir + "testing\\Score.txt", truthRank);
			HashMap<Integer, List<ScoreDTO>> rankList = DatasetUtil.getRankList(evaluationDir + "result\\Score.txt",
					truthRank);

			/**
			 * Four step: compute evaluation
			 */
			evaluationResult = updateEvaluationResult(evaluationResult, computeEvaluation(rankList, groundTruth));
		}

		/**
		 * Fifth step: write result to DB
		 */
		for (String key : evaluationResult.keySet()) {
			evaluationResult.put(key, evaluationResult.get(key) / evaluationParam);
		}
		writeResult(taskId, evaluationResult, true);
	}

	private long estimateCrossValidationTime() {

		long startTime = 0;
		long endTime = 0;
		long runTime = 0;

		startTime = System.currentTimeMillis();

		CollaborativeFilteringDataPreparer dataPreparer = new CollaborativeFilteringDataPreparer(inputDir);
		HashMap<String, Double> evaluationResult = null;

		endTime = System.currentTimeMillis();
		runTime += endTime - startTime;

		/**
		 * First step: preparing data, split training and testing data set
		 */
		startTime = System.currentTimeMillis();

		dataPreparer.splitDataSet(0, evaluationParam, inputDir, evaluationDir);

		endTime = System.currentTimeMillis();
		runTime += endTime - startTime;

		/**
		 * Second step: call Algorithm execute on training data set
		 */
		estimateTrainingTime();
		// runTime +=

		/**
		 * Third step: convert result to boolean type
		 */
		HashMap<Integer, List<ScoreDTO>> groundTruth = DatasetUtil.getGroundTruth(evaluationDir + "testing\\Score.txt",
				truthRank);
		HashMap<Integer, List<ScoreDTO>> rankList = DatasetUtil.getRankList(evaluationDir + "result\\Score.txt",
				truthRank);

		endTime = System.currentTimeMillis();
		runTime += endTime - startTime;
		/**
		 * Four step: compute evaluation
		 */
		evaluationResult = updateEvaluationResult(evaluationResult, computeEvaluation(rankList, groundTruth));

		endTime = System.currentTimeMillis();
		return runTime += endTime - startTime;

	}

	private HashMap<String, Double> updateEvaluationResult(HashMap<String, Double> oldResult,
			HashMap<String, Double> newResult) {
		if (oldResult == null)
			return newResult;
		for (String key : newResult.keySet()) {
			newResult.put(key, newResult.get(key) + oldResult.get(key));
		}
		return newResult;
	}

	public void evaluate() {

		switch (evaluationType) {
		case "cross":
			crossValidation();
			break;
		case "partitioning":
			percentageSplit();
			break;
		default:
			customValidation();
			break;
		}

	}

	private HashMap<String, Double> computeEvaluation(HashMap<Integer, List<ScoreDTO>> rankList,
			HashMap<Integer, List<ScoreDTO>> groundTruth) {
		double preTopN = 0;
		double precision = 0;
		double recall = 0;
		double recTopN = 0;
		double f = 0;
		double ndcgTopN = 0;
		double rmse = 0;
		double mrr = 0;
		double map = 0;
		for (Integer userId : rankList.keySet()) {
			preTopN += Precision.computePrecisionTopN(rankList.get(userId), groundTruth.get(userId), topN);
			precision += Precision.computePrecision(rankList.get(userId), groundTruth.get(userId));
			recall += Recall.computeRecall(rankList.get(userId), groundTruth.get(userId));
			recTopN += Recall.computeRecallTopN(rankList.get(userId), groundTruth.get(userId), topN);
			f += FMeasure.computeF1(rankList.get(userId), groundTruth.get(userId));
			try {
				ndcgTopN += NDCG.computeNDCG(rankList.get(userId), groundTruth.get(userId), topN);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			rmse += RMSE.computeRMSE(rankList.get(userId), groundTruth.get(userId));
			try {
				mrr += ReciprocalRank.computeRR(rankList.get(userId), groundTruth.get(userId));
			} catch (Exception e) {
				e.printStackTrace();
			}
			map += AveragePrecision.computeAP(rankList.get(userId), groundTruth.get(userId));
		}
		int n = rankList.size();
		if (n != 0) {
			preTopN /= n;
			precision /= n;
			recall /= n;
			recTopN /= n;
			f /= n;
			ndcgTopN /= n;
			rmse /= n;
			mrr /= n;
			map /= n;
		}
		System.out.println("P@" + topN + ": " + preTopN);
		System.out.println("P:" + precision);
		System.out.println("R:" + recall);
		System.out.println("R@" + topN + ": " + recTopN);
		System.out.println("F:" + f);
		System.out.println("NDCG@" + topN + ": " + ndcgTopN);
		System.out.println("RMSE:" + rmse);
		System.out.println("MRR:" + mrr);
		System.out.println("MAP:" + map);

		System.out.println("-----------");

		HashMap<String, Double> evaluationResult = new HashMap<>(evaluationParam);
		evaluationResult.put("Precision", precision);
		evaluationResult.put("Recall", recall);
		evaluationResult.put("P@" + topN, preTopN);
		evaluationResult.put("R@" + topN, recTopN);
		evaluationResult.put("F1", f);
		evaluationResult.put("NDCG@" + topN, ndcgTopN);
		evaluationResult.put("RMSE", rmse);
		evaluationResult.put("MRR", mrr);
		evaluationResult.put("MAP", map);

		return evaluationResult;

	}

	private void writeResult(String taskId, HashMap<String, Double> evaluationResult, boolean writeToFile) {
		if (writeToFile) {
			/* Create file */
			File commandFile = new File(evaluationDir + "evaluationResult.txt");
			if (!commandFile.exists()) {
				try {
					commandFile.createNewFile();
					FileWriter fw = new FileWriter(commandFile.getAbsoluteFile());
					BufferedWriter bw = new BufferedWriter(fw);
					for (String key : evaluationResult.keySet()) {
						bw.write(key + "\t" + evaluationResult.get(key) + "\n");
					}
					bw.close();
					fw.close();
				} catch (IOException e) {
					log.error(e);
					e.printStackTrace();
				}
			}
		}

		try {
			MysqlDBConnection con = new MysqlDBConnection("dbconfig.properties");
			if (con.connect()) {
				String sql = "INSERT INTO `evaluation`(`TaskId`, `Score`, `Metric`) VALUES ";
				for (String key : evaluationResult.keySet()) {
					sql += "(" + taskId + "," + evaluationResult.get(key) + ", '" + key + "'),";
				}
				sql = sql.substring(0, sql.length() - 1);
				con.write(sql);
				con.write("update task set ExecutionTime = '" + ((System.currentTimeMillis() - this.startTime)/1000)
					+ "', Status = 'Done' where TaskId = " + taskId);
				con.close();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			log.error(ex);
		}
	}

	private void trainAlgorithm(RecommendationAlgorithm alg) {
		switch (algorithm) {
		case "cf":
			if (alg != null) {
				trainCF((CollaborativeFiltering) alg);
			} else {
				trainCF();
			}
			break;
		case "cb":
			trainCB();
			break;
		case "hb":
			trainHB();
			break;
		default:
			break;
		}
	}

	private HashMap<Long, RecommendationAlgorithm> estimateTrainingTime() {
		isEstimate = true;
		switch (algorithm) {
		case "cf":
			return estimateTrainCFTime();
		case "cb":
			trainCB();
			break;
		case "hb":
			trainHB();
			break;
		default:
			break;
		}
		return null;
	}

	private void trainHB() {
		HybirdRecommeder hybridRecommender = new HybirdRecommeder(inputDir, evaluationDir, taskId, startTime);
		hybridRecommender.setRunningEvaluation(true);
		hybridRecommender.init();
		hybridRecommender.run();
	}

	private void trainCB() {
		CB cb = new CB(inputDir, evaluationDir, taskId, true, startTime);
		cb.setRunningEvaluation(true);
		try {
			cb.run();
		} catch (Exception ex) {
			log.error(ex);
		}
	}

	private void trainCF(CollaborativeFiltering cf) {
		cf.recommend();
	}

	private void trainCF() {
		CollaborativeFiltering cf = new CollaborativeFiltering(evaluationDir, config, taskId, startTime);
		cf.recommend();
	}

	private HashMap<Long, RecommendationAlgorithm> estimateTrainCFTime() {
		long initModelTime = 0;
		long tStart = System.currentTimeMillis();
		CollaborativeFiltering cf = new CollaborativeFiltering(evaluationDir, config, taskId, startTime);
		long tEnd = System.currentTimeMillis();
		initModelTime = tEnd - tStart;
		HashMap<Long, RecommendationAlgorithm> returnMap = new HashMap<>();
		returnMap.put(cf.estimateRecommendationTime(initModelTime), cf);
		return returnMap;
	}
}
