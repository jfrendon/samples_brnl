function [fileNames inSampleIdx outOfSampleIdx] = generateAndSaveNN3(   modelInfo, ...
                                                                        nNets,...
                                                                        dir, ...
                                                                        direction_M,...
                                                                        direction_m,...    
                                                                        rmseAsComponent,...
                                                                        dimensionFilter)

fileNames = [];
[msg flagOkValidations] = validateGenerateModel(modelInfo, ...
                                    nNets,...
                                    dir, ...
                                    direction_M,...
                                    direction_m,...    
                                    rmseAsComponent,...
                                    dimensionFilter);

if(flagOkValidations == true)
    fileNames = cell(1, nNets);
    if(strcmp(modelInfo.implementation, 'MatlabR2010a') == 1)
        
        stepAhead = modelInfo.H;
        nInputs = modelInfo.nInputs;
        
        for i=1:nNets
            seriesReplicationData = getModelInfoReplication(modelInfo, i);
            
            X_ = seriesReplicationData.inputs(:,1:nInputs); %selection of inputs from the prepared data.
            Y_ = seriesReplicationData.outputs(:,stepAhead);  %selection of the output from the prepared data.
            
            X = X_';
            Y = Y_';
            
            netSpec = setNetSpec(struct('name', modelInfo.name, ... 
                'description', modelInfo.description, ...
                'inputData', X, ...
                'outputData', Y, ...
                'nTrain', modelInfo.nTrain, ...
                'nVal', modelInfo.nVal, ...
                'nTest', modelInfo.nTest, ...
                'Nis', modelInfo.Nis, ...
                'Nos', modelInfo.Nos, ...
                'divideDataFcn', modelInfo.divideDataFcn,...,
                'dataConfigType', modelInfo.dataConfigType, ...
                'nNeurons', modelInfo.nNeurons, ...
                'maxEpochs', modelInfo.maxEpochs, ...
                'implementation', modelInfo.implementation, ...
                'networkType', modelInfo.networkType));
            
            
            [xTrain yTrain xFcst yFcst inSampleIdx outOfSampleIdx] = getTrainingConfig(netSpec);
            
%fprintf('inSampleIdx:\n');
%disp(inSampleIdx);

%fprintf('outOfSampleIdx:\n');
%disp(outOfSampleIdx);
        
        if(strcmp(modelInfo.networkType, 'feedforward') == 1)
            netImpl = newfit(xTrain, ...
                             yTrain, ...
                             modelInfo.nNeurons);
        else
            netImpl = newelm(xTrain, ...
                             yTrain, ...
                             modelInfo.nNeurons);
        end
        
        
        %The default loss function is mse.
        %model.nNetworkImp.performFcn = mse
        netImpl.divideFcn = modelInfo.divideDataFcn;
        
        %Approach with precise division of data. Must be used with
        %modelInfo.divideDataFcn = 'divideblock2'
        netImpl.divideParam.nTrain = modelInfo.nTrain;
        netImpl.divideParam.nVal = modelInfo.nVal;
        netImpl.divideParam.nTest = modelInfo.nTest;
        
        %Approach with percentages. Can be used with
        %modelInfo.divideDataFcn = 'divideblock' Other functions are not
        %supported (it is required to keep the out of sample portion at the 
        %end of the data set, so functions like 'dividerand' are not
        %considered yet.
        netImpl.divideParam.trainRatio = modelInfo.Ptr;
        netImpl.divideParam.valRatio = modelInfo.Pva;
        netImpl.divideParam.testRatio = modelInfo.Pte;
        netImpl.trainParam.epochs = modelInfo.maxEpochs;
       
%         if(~isempty(modelInfo.epochs))
%             netImpl.trainParam.epochs = modelInfo.epochs;
%         end
        netImpl.trainParam.showWindow = modelInfo.showWindow;
        netImpl = train(netImpl, xTrain, yTrain);
        ySim = sim(netImpl, xFcst);
        
        %net = nNetwork();
        %net.nNeurons = modelInfo.nNetwork.nNeurons;
        %net.vMlabR2010a = netImpl;
        %model = Fmodel(strcat('Net ', int2str(i)), 'NN');
        
        %model = getStructGenModel(struct('name', strcat('Net ', int2str(i)), ...
        %    'type', 'NN'));
        
        
        model = getStructGenModel(struct('name', 'Net', 'type', 'NN'));
        model.description = modelInfo.description;
        model.nTrainData = modelInfo.nTrain;
        model.nValData = modelInfo.nVal;
        model.nTestData = modelInfo.nTest;
        
        model.Ptr = modelInfo.Ptr;
        model.Pva = modelInfo.Pva;
        model.Pte = modelInfo.Pte;
        model.Nis = modelInfo.Nis;
        model.Nos = modelInfo.Nos;
        model.dataConfigType = modelInfo.dataConfigType;
        model.inputData = X;
        model.outputData = Y;
        model.divideDataFcn = modelInfo.divideDataFcn;
        model.idxInSample = inSampleIdx;
        %disp('inSampleIdx');
        %disp(inSampleIdx);
        model.idxOutOfSample = outOfSampleIdx;
        %disp('outOfSampleIdx');
        %disp(outOfSampleIdx);
        model.forecasted = ySim;
        model.idxForecasted = 1:length(model.inputData);
        model.residuals =  model.outputData - ySim;
        model.nNetworkImpDesc = 'vMlabR2010a';
        model.nNetworkImp = netImpl;
        model = setPerformance(model);
        
        %The vectorial representation is set.
        model.v = NNmodelsToVectors([], {model}, direction_M, direction_m, rmseAsComponent, dimensionFilter);
        fileName = strcat(dir, '/', 'model_net_', int2str(i), '_',datestr(now, 'dd_mm_yyyy_HH_MM'));
        save(fileName, 'model');
        fileNames{1, i} = fileName;
                
        end
    elseif(strcmp(implementation, 'NetLab3_3') == 1)
        %TODO: implement taking into account the changes with respect
        %to the test data. In MatlabR2010a implementation the test data
        %was use for out-of-sample forecasting.
        error('NetLab3_3 implementation is not avalible.');
        
    end %Other implementations can be made if neccesary.
else
    error(msg);
end
end

function [msg flagOkValidations] = validateGenerateModel(modelInfo, ...
                                    nNets,...
                                    dir, ...
                                    direction_M,...
                                    direction_m,...    
                                    rmseAsComponent,...
                                    dimensionFilter)
msg = '';
flagOkValidations = true;
okContinue = true;

if(isempty(modelInfo))
    flagOkValidations = false;
    msg = strcat(msg, 'Parameter ''modelInfo'' must not be empty.');
    okContinue = false;
end

%Eliminated the validatio of type. The class 'Fmodel' is being
%replaced by a struct.



if(okContinue)
    if(isempty(modelInfo.seriesRepFileName))
        if(isempty(modelInfo.inputData) || isempty(modelInfo.outputData))
            flagOkValidations = false;
            msg = strcat(msg, 'Nor Inputs (modelInfo.inputData) or target (modelInfo.outputData) must be empty.');
            okContinue = false;
        end
    end
end


if(okContinue)
    %With the changes regarding the test data, other ways to
    %partition the data can be allowed.
    if(isempty(modelInfo.divideDataFcn))
        flagOkValidations = false;
        msg = strcat(msg, 'modelInfo.divideDataFcn must not be empty.');
        okContinue = false;
        
    end
end



if(okContinue)
    if(isempty(modelInfo.implementation))
        flagOkValidations = false;
        okContinue = false;
        msg = strcat(msg, 'Parameter ''modelInfo.implementation'' must not be empty.');
    end
end

if(okContinue)
    if(strcmp(modelInfo.implementation, 'MatlabR2010a')== 0 && strcmp(modelInfo.implementation, 'NetLab3_3')== 0)
        flagOkValidations = false;
        okContinue = false;
        msg = strcat(msg, 'Value for parameter ''modelInfo.implementation'' (', modelInfo.implementation, ') is not valid.');
    end
end

if(okContinue)
    if(isempty(modelInfo.networkType))
        flagOkValidations = false;
        okContinue = false;
        msg = strcat(msg, 'Parameter ''modelInfo.networkType'' must not be empty.');
    end
end


if(okContinue)
    if(strcmp(modelInfo.networkType, 'feedforward') == 0 && strcmp(modelInfo.networkType, 'elman') == 0)
        flagOkValidations = false;
        okContinue = false;
        msg = strcat(msg, 'Parameter ''modelInfo.networkType'' must have a valid value (''feedforward'' or ''elman'').');
    end
end

if(okContinue)
    if(strcmp(modelInfo.networkType, 'elman') == 1 && strcmp(modelInfo.implementation, 'NetLab3_3') == 1)
        flagOkValidations = false;
        okContinue = false;
        msg = strcat(msg, 'Generation of models not suported for elman nets with NetLab3_3.');
    end
end

if(okContinue)
    if( isempty(modelInfo.nNeurons))
        flagOkValidations = false;
        okContinue = false;
        msg = strcat(msg, 'modelInfo.nNeurons must not be empty.');
    end
end

if(okContinue)
    if(isempty(modelInfo.maxEpochs) || modelInfo.maxEpochs < 1)
        flagOkValidations = false;
        okContinue = false;
        msg = strcat(msg, 'Parameter ''modelInfo.maxEpochs'' must not be empty or less than 1.');
    end
end



end
