package com.taskRecommendation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import com.data.Constants;
import com.data.TestProject;
import com.dataProcess.TestProjectReader;
import com.recommendBasic.WorkerActiveHistory;
import com.recommendBasic.WorkerExpertiseHistory;
import com.recommendBasic.WorkerPreferenceHistory;
import com.wekaPrediction.WekaPrediction;


public class PredictWorkerPerformance {
	LinkedHashMap<Date, ArrayList<TestProject>> trainProjectList;
	LinkedHashMap<Date, ArrayList<TestProject>> testProjectList;
	Integer testBeginIndex = 50;  
	String type = "six";
	
	public PredictWorkerPerformance ( ){
		trainProjectList = new LinkedHashMap<Date, ArrayList<TestProject>>();
		testProjectList = new LinkedHashMap<Date, ArrayList<TestProject>>();
	}
	
	public void separateTrainTestSet ( ArrayList<TestProject> projectList ){
		RecommendationTime recTimeTool = new RecommendationTime ();
		LinkedHashMap<Date, ArrayList<TestProject>> recTimeByProjects = recTimeTool.obtainMultiTaskStatus(projectList);
		
		int trainCount = 0;
		for ( Date curTime : recTimeByProjects.keySet() ){
			trainCount++;
			ArrayList<TestProject> curProjectList = recTimeByProjects.get( curTime );
			if ( trainCount < testBeginIndex ){   /////
				trainProjectList.put( curTime, curProjectList );
			}else{
				testProjectList.put( curTime, curProjectList );
			}			
		}
	}
	
	public void trainModelSemantic (  ){
		WorkerActiveHistory actHistory = new WorkerActiveHistory();
		HashMap<String, HashMap<Date, ArrayList<String>>> workerActiveHistory = actHistory.readWorkerActiveHistory( "data/output/history/active.txt" ) ; 
		WorkerExpertiseHistory expHistory = new WorkerExpertiseHistory();
		HashMap<String, HashMap<Date, Double[]>> workerExpertiseHistory = expHistory.readWorkerExpertiseHistorySemantic( "data/output/history/semanticExpertise.txt" );
		WorkerPreferenceHistory prefHistory = new WorkerPreferenceHistory();
		HashMap<String, HashMap<Date, Double[]>> workerPreferenceHistory = prefHistory.readWorkerPreferenceHistorySemantic( "data/output/history/semanticPreference.txt" );
		
		FeaturePreparationSemantic featurePrepareTool = new FeaturePreparationSemantic();
		//Ŀǰ��i��training set��ֻ�Ǵ洢�˵�i���ȵ�i-1����Ĳ��֣�Ӧ�ðѵ�i-1���͵�i���ϲ���ʹ��utility/FileCombination��
		int index = 0;
		for ( Date curTime : trainProjectList.keySet() ){
			int group = (++index) / 10;
			String trainFile = "data/output/weka2/train-" + type + "/train-" + group + ".csv";
			
			System.out.println ( "curTime is " + curTime );
			ArrayList<TestProject> curProjectList = trainProjectList.get( curTime );
			//ÿ����Ŀ�䵱һ��curProject
			
			for ( int i =0; i < curProjectList.size(); i++ ){
				TestProject curProject = curProjectList.get( i );
				System.out.println ( "curProject is " + curProject.getProjectName() );
				
				int curRecPoint = featurePrepareTool.findRecPointByTime( curProject , curTime);
				ArrayList<TestProject> openProjectList = new ArrayList<TestProject>();
				for ( int j =0; j < curProjectList.size(); j++ ){
					if ( i == j )
						continue;
					openProjectList.add( curProjectList.get(j) );
				}
				featurePrepareTool.prepareLearningFeatures(curProject, curRecPoint, trainFile, true, openProjectList, workerActiveHistory, workerExpertiseHistory, workerPreferenceHistory);
			}
		}		
	}
	
	public void conductPredictionSemantic (  ){
		WekaPrediction prediction = new WekaPrediction ();
		TaskRecommendationAndPerformance performanceTool = new TaskRecommendationAndPerformance();
		
		WorkerActiveHistory actHistory = new WorkerActiveHistory();
		HashMap<String, HashMap<Date, ArrayList<String>>> workerActiveHistory = actHistory.readWorkerActiveHistory( "data/output/history/active.txt" ) ; 
		WorkerExpertiseHistory expHistory = new WorkerExpertiseHistory();
		HashMap<String, HashMap<Date, Double[]>> workerExpertiseHistory = expHistory.readWorkerExpertiseHistorySemantic( "data/output/history/semanticExpertise.txt" );
		WorkerPreferenceHistory prefHistory = new WorkerPreferenceHistory();
		HashMap<String, HashMap<Date, Double[]>> workerPreferenceHistory = prefHistory.readWorkerPreferenceHistorySemantic( "data/output/history/semanticPreference.txt" );
		
		FeaturePreparationSemantic featurePrepareTool = new FeaturePreparationSemantic();
		ArrayList<String> workerIdList = new ArrayList<String>();
		int index = testBeginIndex;
		for ( Date curTime : testProjectList.keySet() ){
			int trainIndex = (index - 10) / 10;
			String trainFile = "data/output/weka2/train-" +  type + "/train-" + trainIndex + ".csv";
			
			System.out.println ( "curTime is " + curTime );
			ArrayList<TestProject> curProjectList = testProjectList.get( curTime );
			//ÿ����Ŀ�䵱һ��curProject������ĳ��time������һ��Ԥ��
			
			String testFile = "data/output/weka2/test-" + type + "/test-" + index + ".csv";
			File file = new File ( testFile );
			if ( !file.exists()){
				for ( int i =0; i < curProjectList.size(); i++ ){  
					TestProject curProject = curProjectList.get( i );
					System.out.println ( "curProject is " + curProject.getProjectName() );
					
					int curRecPoint = featurePrepareTool.findRecPointByTime( curProject , curTime);
					ArrayList<TestProject> openProjectList = new ArrayList<TestProject>();
					for ( int j =0; j < curProjectList.size(); j++ ){
						if ( i == j )
							continue;
						openProjectList.add( curProjectList.get(j) );
					}
					ArrayList<String> featureWorkers  = featurePrepareTool.prepareLearningFeatures(curProject, curRecPoint, testFile, false,  
							openProjectList,  workerActiveHistory, workerExpertiseHistory, workerPreferenceHistory);
					
					for ( int j =0; j < featureWorkers.size(); j++ ){
						workerIdList.add( curProject.getProjectName() + "----" + featureWorkers.get(j));
					}
				}
			}else{
				//��Ҫ��ȡ��Ӧ��workerIdList, ��֮ǰresultFile�л�ȡ
				try {
					BufferedReader reader = new BufferedReader ( new FileReader ( new File ( "data/output/weka2/result-" + type + "/result-" + index + ".csv" )));
					String line = null;
					line = reader.readLine();
					while (  (line = reader.readLine()) != null ){				
						String[] temp = line.split(",");
						String workerId = temp[1];
						workerIdList.add( workerId );
					}
					reader.close();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}			
			
			//��trainAndPredictProb��˳��洢��result file
			HashMap<String, String[]> predictDetailList = prediction.trainAndPredictProb( trainFile, testFile, workerIdList, "RandomForest");
			performanceTool.computeRecommendationPrecisionRecall(predictDetailList, "data/output/weka2/performance-" + type  + "/performance-" + index + ".csv" );
			
			index++;
		}		
	}
	
	public static void main(String[] args) {
		PredictWorkerPerformance taskRecTool = new PredictWorkerPerformance ();
		
		TestProjectReader projectReader = new TestProjectReader();
		ArrayList<TestProject> projectList = projectReader.loadTestProjectAndTaskList( Constants.PROJECT_FOLDER, Constants.TASK_DES_FOLDER );
		taskRecTool.separateTrainTestSet(projectList);
		
		//taskRecTool.trainModelSemantic ( );
		
		taskRecTool.conductPredictionSemantic();
	}
}