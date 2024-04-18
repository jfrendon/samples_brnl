s = RandStream('mt19937ar','Seed',7350);
RandStream.setDefaultStream(s);

numMaxFuncionesBase = 4;
nNetsPool = 50;
nModelsPerCluster = 5;
outputFolder = 'd:/temp/ensemble_iterative';

H = 24; %used in the generation of data files.
lags = [1 2 24 25 48 72 96 120 144 168 192 216 240 264 312 336];
fhorizon = 12;
maxEpochs =  4000;
trainingSize = 3360; %same as in the script to generate the replications.

y_series = dlmread('TayloretalRiodataLoadOnly_corrected2.txt');
a = 24;
b = 168;
yf = y_series ...
     - lagmatrix(y_series, a) ...
     - lagmatrix(y_series, 1) ...
     + lagmatrix(y_series, a+1) ...
     - lagmatrix(y_series, b) ...
     + lagmatrix(y_series, a+b) ...
     + lagmatrix(y_series, b+1) ...
     - lagmatrix(y_series, a+b+1);

rollingStep = 1;
Exo = []; 
xtremeValConf = []; 

s = [NaN(336,1); yf ; NaN(336,1)];

[originalIndex inputs outputs] = prepareMultiStepData( Exo, s, lags, H, rollingStep);
    
configAndResults = struct('sizeSeries', numel(s), ...
    'cutPoint', 3360+H, ...
    'idxAhead', 3361, ...
    'H', H, ...
    's', s, ...
    'inputs', inputs, ...
    'outputs', outputs, ...
    'originalIndex', originalIndex,...
    'xtremeValConf', xtremeValConf);

Pva = 0.1;
a = 24; %season1
b = 168; %season2

divideDataFcn = 'divideblock2';
dataConfigType = 'testEqualOutOfSample'; 
implementation = 'MatlabR2010a';
networkType = 'feedforward';
fieldNameData = 'configAndResults'; %name of the field containing data in stored files.

sData = configAndResults;

selectionInputs = 1:16;
nNeurons = 6;

%X_all = sData.inputs; %selection of inputs from the prepared data.
X_ = sData.inputs(:,selectionInputs);
Y_ = sData.outputs(:,1);  % 1 s. ahead for training

splitPointMultiStep = sData.idxAhead;

%Dividing data in in-sample and out-of-sample is not
%necesary here, but it is done for clarity.
outOfSampleX = X_(splitPointMultiStep:end,:);
outOfSampleY = Y_(splitPointMultiStep:end,:);

inSampleX = X_(1:splitPointMultiStep - 1,:);
inSampleY = Y_(1:splitPointMultiStep - 1,:);

inan_start_ = sum(isnan(inSampleX),2);
inan_start = max(find(inan_start_ > 0));
fprintf('inan_start: %d\n', inan_start);
inan_end_ = sum(isnan(outOfSampleY),2);
inan_end = min(find(inan_end_ > 0));
fprintf('inan_end: %d\n', inan_end);

inSampleX_clean = inSampleX(inan_start+1:end,:);
outOfSampleX_clean = outOfSampleX(1:inan_end-1,:);

inSampleY_clean = inSampleY(inan_start+1:end,:);
outOfSampleY_clean = outOfSampleY(1:inan_end-1,:);

x_ = [inSampleX_clean; outOfSampleX_clean];
y_ = [inSampleY_clean; outOfSampleY_clean];

%fprintf('a---------------\n');
x = x_';
y = y_';
%fprintf('b---------------\n');

numNan = sum(sum(isnan(x),1),2) + sum(sum(isnan(y),1),2);
fprintf('******numNan: %d\n', numNan);
%if(numNan == 0)
nTest = size(outOfSampleX_clean,1);
nVal = floor(Pva*size(inSampleX_clean,1));
nTrain = size(inSampleX_clean,1) - nVal;


netSpec = setNetSpec(struct('name', 'Base NN model', ...
    'description', '', ...
    'inputData', x, ...
    'outputData', y, ...
    'nTrain', nTrain, ...
    'nVal', nVal, ...
    'nTest', nTest, ...
    'Nis', nTrain+nVal, ...
    'Nos', nTest, ...
    'divideDataFcn', divideDataFcn,...,
    'dataConfigType', dataConfigType, ...
    'nNeurons', nNeurons, ...
    'maxEpochs', maxEpochs, ...
    'implementation', implementation, ...
    'networkType', networkType, ...
    'bayesianLearning', 1));

netSpec.y = y_series;
netSpec.y_diff = yf;
netSpec.lags = lags;
netSpec.a = a;
netSpec.b = b;
netSpec.trsize = trainingSize; %training size for the original series.
netSpec.fhorizon = fhorizon;

%%

m = setClusterCmbNNIterativeModel(struct('numMaxFuncionesBase', numMaxFuncionesBase,...
                                'netSpec', netSpec, ...
                                'seriesData', sData, ...
                                'inputData', x, ... 
                                'outputData', y, ...
                                ...%'nBootstraps', nBootstraps, ... 
                                'nNets', nNetsPool, ...
                                'nModelsPerCluster', nModelsPerCluster, ...
                                'direction_M', 'row',...
                                'direction_m', 'row', ...
                                'rmseAsComponent', false, ...    
                                'dimensionFilter', [], ...
                                'fhorizon', fhorizon, ...
                                'dir', outputFolder));
%%

%copy of models used is in
%j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\

m.netsFileNames = {'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag50_19_01_2016_09_46.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag44_19_01_2016_09_23.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag43_19_01_2016_09_19.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag40_19_01_2016_09_07.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag38_19_01_2016_09_00.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag34_19_01_2016_08_44.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag32_19_01_2016_08_36.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag29_19_01_2016_08_21.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag28_19_01_2016_08_17.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag25_19_01_2016_08_05.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag24_19_01_2016_08_02.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag15_19_01_2016_07_28.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag12_19_01_2016_07_17.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag7_19_01_2016_06_59.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag3_19_01_2016_06_45.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag2_19_01_2016_06_42.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag1_19_01_2016_06_39.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag43_19_01_2016_03_27.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag42_19_01_2016_03_12.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag41_19_01_2016_03_07.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag48_19_01_2016_02_50.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag47_19_01_2016_02_38.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag46_19_01_2016_02_34.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag27_19_01_2016_02_16.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag42_19_01_2016_02_07.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag41_19_01_2016_02_03.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag25_19_01_2016_02_01.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag38_19_01_2016_01_52.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag37_19_01_2016_01_48.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag35_19_01_2016_01_41.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag19_19_01_2016_01_38.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag34_19_01_2016_01_37.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag33_19_01_2016_01_33.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag29_19_01_2016_01_18.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag16_19_01_2016_01_09.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag27_19_01_2016_01_03.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag26_19_01_2016_00_59.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag24_19_01_2016_00_51.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag11_19_01_2016_00_50.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag22_19_01_2016_00_44.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag21_19_01_2016_00_40.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag9_19_01_2016_00_34.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag19_19_01_2016_00_33.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag18_19_01_2016_00_29.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag14_19_01_2016_00_15.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag13_19_01_2016_00_11.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag12_19_01_2016_00_08.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag11_19_01_2016_00_03.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag7_18_01_2016_23_46.mat'
'j:\working_folders\research_project\resultsCombinationAfterSensitivityRio_iterative\pool_selected_is_multi_step\model_NNIter_flag1_18_01_2016_23_23.mat'};
%%
m = fitClusterCmbNNIterativeModel(m);

%%
wsFileName = strcat(outputFolder, '/ws');
save(wsFileName);

fprintf('Finished script. Date and time(dd_mm_yyyy_HH_MM): %s\n', datestr(now, 'dd_mm_yyyy_HH_MM'));
