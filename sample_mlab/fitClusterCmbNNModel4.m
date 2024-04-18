% Fits a model based on clustering of neural networks.
% Set to work with recursive partitioning.
% Modified from previous versions in order to integrate:
% 1. replications of data, adding noise
% 2. model generation.
% 3. model filtering, based on correlation.
% 4. combination.

%The functions and structures comprise the logic previously mentioned.
function M = fitClusterCmbNNModel4( m )

if(isempty(m.netsFileNames))
    
    if(~isempty(m.nNetsPlusFactor))  %over produce and select a subset.
        nNets_ = ceil(m.nNetsPlusFactor*m.nNets);
         
        nTotalSeries = nNets_;
        nNewSeries = nTotalSeries-1;
        

        seriesWithNoise_ = prepareMultiStepDataNoise(   m.seriesData, ...
                                                        nNewSeries, ...
                                                        m.fracStdDev, ...
                                                        m.seriesData.H, ... %12
                                                        m.seriesData.lags, ...  %1:12;
                                                        m.seriesData.rollingStep ... % 1;
                                                      );

                                  
        % Put together the initial series and the replications.
        seriesWithNoise = cell(1, nTotalSeries);
        seriesWithNoise{1} = m.seriesData;
        seriesWithNoise(2:end) = seriesWithNoise_(:);
        
        fileNameNewSetSeries = strcat(m.dir, '/', 'seriesWithNoise');
        save(fileNameNewSetSeries, 'seriesWithNoise');
        m.netSpec.seriesRepFileName =  fileNameNewSetSeries;

    
        [fileNames_ inSampleIdx outOfSampleIdx] = generateAndSaveNN3(   m.netSpec, ... 
                                                                        nNets_,...
                                                                        m.dir, ...
                                                                        m.direction_M,...
                                                                        m.direction_m',...    
                                                                        m.rmseAsComponent,...
                                                                        m.dimensionFilter );
        methodFiltering = 'simplePairwiseCorrelation';                      
        explorationCorrelation = exploreFilteringModels( fileNames_, ...
                                                         outOfSampleIdx, ...
                                                         methodFiltering, ...
                                                         [] );
                                                 
        exploredNModels = explorationCorrelation(2,:);
        idxPossibleThresholds = exploredNModels >= m.nNets;
        selectedThresholds = explorationCorrelation(:, idxPossibleThresholds);
        corrThreshold_ = selectedThresholds(1,1);
        m.corrThreshold = corrThreshold_;
    
        [ranking fileNames] = getTopModelsFiltered(  fileNames_, ...
                                                     outOfSampleIdx, ... 
                                                     corrThreshold_, ...
                                                     methodFiltering, ...
                                                     m.nNets ...
                                                  );
   
    else 
        nTotalSeries = m.nNets;
        nNewSeries = nTotalSeries-1;
        seriesWithNoise_ = prepareMultiStepDataNoise(   m.seriesData, ...
                                                        nNewSeries, ...
                                                        m.fracStdDev, ...
                                                        m.seriesData.H, ... %12
                                                        m.seriesData.lags, ...  %1:12;
                                                        m.seriesData.rollingStep ... % 1;
                                                      );

                                  
        % Put together the initial series and the replications.
        seriesWithNoise = cell(1, nTotalSeries);
        seriesWithNoise{1} = m.seriesData;
        seriesWithNoise(2:end) = seriesWithNoise_(:);
        
        fileNameNewSetSeries = strcat(m.dir, '/', 'seriesWithNoise');
        save(fileNameNewSetSeries, 'seriesWithNoise');
        m.netSpec.seriesRepFileName =  fileNameNewSetSeries;
        
        
        [fileNames inSampleIdx outOfSampleIdx] = generateAndSaveNN3(    m.netSpec, ...
                                                                        m.nNets,...
                                                                        m.dir, ...
                                                                        m.direction_M,...
                                                                        m.direction_m',...    
                                                                        m.rmseAsComponent,...
                                                                        m.dimensionFilter  );
    end
    m.netsFileNames = fileNames; %taken from the configuration of the networks and added to the ensemble.
    m.idxInSample = inSampleIdx; %taken from the configuration of the networks and added to the ensemble.
    m.idxOutOfSample = outOfSampleIdx;
    
else
    error('inSampleIdx outOfSampleIdx not set.'); %TO-DO: pending to set inSampleIdx outOfSampleIdx
    fileNames = m.netsFileNames;
end


%Extracts the weights from the internal representations,
%Puts them in the form of a single matrix with each raw
%corresponding to a data point.
%v = NNmodelsToVectors(fileNames, [], m.direction_M, m.direction_m, m.rmseAsComponent, m.dimensionFilter);
%m.V = v;

v1 = getV(fileNames{1});
dimV = numel(v1);
nModels = numel(fileNames);
allV = zeros(nModels, dimV);
for iModel = 1:nModels
    allV(iModel,:) = getV(fileNames{iModel});
end
m.V = allV;

[fb params lof] = recPartitioning(m); %TODO: implement.
m.Fb = fb;
m = setCentres(m);
m.alpha = params; %needs change if other sets of parameters are added.
m = setModelsPerCluster(m); %sets the functions per cluster (m.M).
                            %TODO: implement.


%Optimization step.

options = optimset( 'fmincon' );
options = optimset( options         , ...
'Display'     ,  m.optDisplay    , ...
'MaxFunEvals' ,  m.maxFunEvals  , ...
'MaxIter'     ,  m.maxIter      , ...
'TolFun'      ,  m.tolFun       , ...
'TolCon'      ,  m.tolCon       , ...
'TolX'        ,  m.tolX         , ...
'Hessian'     ,  'off'          , ...
'LargeScale'  ,  'off'          , ...
'GradObj'     ,  'off'      );


[W A b LB UB] = getOptimizationParams(m);  %Gets parameters to adjust plus restrictions
                                           %for the optimization function.
                                           %TODO: implement. W must be a
                                           %column vector.
idx = getInSampleIdx(m);
x = m.inputData(:, idx);
y = m.outputData(:, idx);

%Centres are kept constant in the optimization.
[W, fval, exitflag, output, lambda, grad, hessian] = ...
            fmincon( @lossFcnOptim, W, A, b, [], [], LB, UB, [], options, m, x, y);

alpha = getParamsFromOptimization(W, m); 
                                        

m.alpha = alpha;
m.Phi = getPhi(m);

yEst = evaluate(m, m.inputData);
[xM xN] = size(m.inputData);
m.idxForecasted = (1:xN);
m.forecasted = yEst;
m.residuals = m.outputData - m.forecasted;
m = setPerformance(m);

M = m;

end


function r = lossFcnOptim(w, m, x, y)
yEst = evaluateFromOptim(w, m, x, y);
r = multiSeriesMse(y, yEst, 1); %TODO: change loss function.

end


