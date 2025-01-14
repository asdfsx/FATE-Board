/*
 * Copyright 2019 The FATE Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fedai.fate.board.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.fedai.fate.board.global.Dict;
import org.fedai.fate.board.global.ErrorCode;
import org.fedai.fate.board.global.ResponseResult;
import org.fedai.fate.board.log.LogFileService;
import org.fedai.fate.board.services.FlowFeign;
import org.fedai.fate.board.services.JobDetailService;
import org.fedai.fate.board.services.JobManagerService;
import org.fedai.fate.board.services.TaskManagerService;
import org.fedai.fate.board.utils.ResponseUtil;
import org.apache.commons.lang3.StringUtils;
import org.fedai.fate.board.pojo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;


@Controller
@RequestMapping(value = "/v1")
public class JobDetailController {

    private final Logger logger = LoggerFactory.getLogger(JobDetailController.class);

    @Autowired
    TaskManagerService taskManagerService;

    @Autowired
    JobDetailService jobDetailService;

    @Autowired
    JobManagerService jobManagerService;

    @Value("${fateflow.url}")
    String fateUrl;

    @Autowired
    FlowFeign flowFeign;

    @ResponseBody
    @RequestMapping(value = "/tracking/component/config", method = RequestMethod.GET)
    public ResponseResult queryJobStatus(@RequestParam("componentName") String componentName) {
        String result;
        try {
            result = jobDetailService.getComponentStaticInfo(componentName);
        } catch (Exception e) {
            logger.error("get component file info fail:", e);
            return new ResponseResult<>(ErrorCode.FILE_ERROR);
        }

        JSONObject resultObject = JSON.parseObject(result);
        return new ResponseResult<>(resultObject);
    }


    @ResponseBody
    @RequestMapping(value = "/tracking/component/metrics", method = RequestMethod.POST)
    public ResponseResult getMetaInfo(@Valid @RequestBody ComponentQueryDTO componentQueryDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            FieldError errors = bindingResult.getFieldError();
            return new ResponseResult<>(ErrorCode.ERROR_PARAMETER, errors.getDefaultMessage());
        }
        Preconditions.checkArgument(LogFileService.checkPathParameters(componentQueryDTO.getJob_id(), componentQueryDTO.getRole(), componentQueryDTO.getParty_id(), componentQueryDTO.getComponent_name()));

        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put(Dict.JOBID, componentQueryDTO.getJob_id());
        reqMap.put(Dict.ROLE, componentQueryDTO.getRole());
        reqMap.put(Dict.PARTY_ID, componentQueryDTO.getParty_id());
        reqMap.put(Dict.TASK_NAME, componentQueryDTO.getComponent_name());
        String result;
        try {

            //generateURLParamJobQueryDTO
            result = flowFeign.get(Dict.URL_COPONENT_METRIC, reqMap);
        } catch (Exception e) {
            logger.error("connect fateflow error:", e);
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_CONNECTION);
        }
        return ResponseUtil.buildResponse(result, Dict.DATA);
    }

    @RequestMapping(value = "/tracking/component/metric_data", method = RequestMethod.POST)
    @ResponseBody
    public ResponseResult getMetricInfo(@Valid @RequestBody MetricDTO metricDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            FieldError errors = bindingResult.getFieldError();
            return new ResponseResult<>(ErrorCode.ERROR_PARAMETER, errors.getDefaultMessage());
        }
        Preconditions.checkArgument(LogFileService.checkPathParameters(metricDTO.getJob_id(), metricDTO.getRole(), metricDTO.getParty_id(), metricDTO.getComponent_name(), metricDTO.getMetric_name(), metricDTO.getMetric_namespace()));

        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put(Dict.JOBID, metricDTO.getJob_id());

        reqMap.put(Dict.ROLE, metricDTO.getRole());
        reqMap.put(Dict.PARTY_ID, metricDTO.getParty_id());
        reqMap.put(Dict.TASK_NAME, metricDTO.getComponent_name());
        reqMap.put(Dict.METRIC_NAME, metricDTO.getMetric_name());
        reqMap.put(Dict.METRIC_NAMESPACE, metricDTO.getMetric_namespace());
        String result;

        try {
            result = flowFeign.get(Dict.URL_COPONENT_METRIC_DATA, reqMap);
        } catch (Exception e) {
            logger.error("connect fateflow error:", e);
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_CONNECTION);
        }
        return ResponseUtil.buildResponse(result, null);
    }


    @RequestMapping(value = "/tracking/component/parameters", method = RequestMethod.POST)
    @ResponseBody
    public ResponseResult getDetailInfo(@Valid @RequestBody ComponentQueryDTO componentQueryDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            FieldError errors = bindingResult.getFieldError();
            return new ResponseResult<>(ErrorCode.ERROR_PARAMETER, errors.getDefaultMessage());
        }
        Preconditions.checkArgument(LogFileService.checkPathParameters(componentQueryDTO.getJob_id(), componentQueryDTO.getRole(), componentQueryDTO.getParty_id(), componentQueryDTO.getComponent_name()));

        String taskId = componentQueryDTO.getJob_id() + "_" + componentQueryDTO.getComponent_name();
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(Dict.JOBID, componentQueryDTO.getJob_id());
        paramMap.put(Dict.TASK_ID, taskId);

        String result = null;
        try {
            result = flowFeign.get(Dict.URL_TASK_DATAVIEW, paramMap);
        } catch (Exception e) {
            logger.error("connect fateflow error:", e);
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_CONNECTION);
        }
        if (StringUtils.isEmpty(result)) {
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_NULL_RESULT);
        }
        JSONObject resultObject = JSON.parseObject(result);
        Integer retcode = resultObject.getInteger(Dict.CODE);
        String msg = resultObject.getString(Dict.RETMSG);

        JSONObject detail = (JSONObject) resultObject.getJSONArray(Dict.DATA).get(0);
        JSONObject newData = detail.getJSONObject(Dict.COMPONENT_PARAMETERS);

        if (newData != null) {
            JSONObject conf = newData.getJSONObject("conf");
            if (conf != null) {
                JSONObject federation = conf.getJSONObject("federation");
                if (federation != null) {
                    JSONObject metadata = federation.getJSONObject("metadata");
                    if (metadata != null) {
                        JSONObject rollSiteConfig = metadata.getJSONObject("rollsite_config");
                        if (rollSiteConfig != null) {
                            String host = rollSiteConfig.getString("host");
                            if (null != host) {
                                newData.getJSONObject("conf").getJSONObject("federation").getJSONObject("metadata").getJSONObject("rollsite_config").put("host", "xxx.xxx.xxx.xxx");
                            }
                        }
                        JSONObject osxConfig = metadata.getJSONObject("osx_config");
                        if (osxConfig != null) {
                            String host = osxConfig.getString("host");
                            if (null != host) {
                                newData.getJSONObject("conf").getJSONObject("federation").getJSONObject("metadata").getJSONObject("osx_config").put("host", "xxx.xxx.xxx.xxx");
                            }
                        }
                    }
                }
                JSONObject computing = conf.getJSONObject("computing");
                if (computing != null) {
                    JSONObject metadata = computing.getJSONObject("metadata");
                    if (metadata != null) {
                        String host = metadata.getString("host");
                        if (null != host) {
                            newData.getJSONObject("conf").getJSONObject("computing").getJSONObject("metadata").put("host", "xxx.xxx.xxx.xxx");
                        }
                    }
                }
            }
            JSONObject mlmd = newData.getJSONObject("mlmd");
            if (null != mlmd) {
                JSONObject metadata = mlmd.getJSONObject("metadata");
                if (null != metadata) {
                    String host = metadata.getString("host");
                    if (null != host) {
                        newData.getJSONObject("mlmd").getJSONObject("metadata").put("host", "xxx.xxx.xxx.xxx");
                    }
                }
            }
        }

        String dataWithNull = JSON.toJSONString(newData, SerializerFeature.WriteMapNullValue);

        return new ResponseResult<>(retcode, msg, dataWithNull);

    }


    @RequestMapping(value = "/pipeline/dag/dependencies", method = RequestMethod.POST)
    @ResponseBody
    public ResponseResult getDagDependencies(@Valid @RequestBody JobQueryDTO jobQueryDTO, BindingResult bindingResult) {
        //check and get parameters

        if (bindingResult.hasErrors()) {
            FieldError errors = bindingResult.getFieldError();
            return new ResponseResult<>(ErrorCode.ERROR_PARAMETER, errors.getDefaultMessage());
        }
        Preconditions.checkArgument(LogFileService.checkPathParameters(jobQueryDTO.getJob_id(), jobQueryDTO.getRole(), jobQueryDTO.getParty_id()));

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(Dict.JOBID, jobQueryDTO.getJob_id());
        paramMap.put(Dict.ROLE, jobQueryDTO.getRole());
        paramMap.put(Dict.PARTY_ID, jobQueryDTO.getJob_id());

        String result;
        try {
            result = flowFeign.get(Dict.URL_DAG_DEPENDENCY, paramMap);
        } catch (Exception e) {
            logger.error("connect fateflow error:", e);
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_CONNECTION);
        }

        if ((result == null) || 0 == result.trim().length()) {
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_NULL_RESULT);
        }

        JSONObject resultObject = JSON.parseObject(result);
        Integer retCode = resultObject.getInteger(Dict.CODE);
        if (retCode == null) {
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_WRONG_RESULT);
        }

        if (retCode == 0) {
            JSONObject data = resultObject.getJSONObject(Dict.DATA);
            JSONArray components_list = data.getJSONArray(Dict.COMPONENT_LIST);
            ArrayList<Map<String, Object>> componentList = new ArrayList<>();

            for (Object o : components_list) {
                HashMap<String, Object> component = new HashMap<>();
                component.put(Dict.COMPONENT_NAME, o);
                TaskDO task = taskManagerService.findTask(jobQueryDTO.getJob_id(), jobQueryDTO.getRole(), jobQueryDTO.getParty_id(), (String) o);
                String taskStatus = null;
                Long createTime = null;
                if (task != null) {
                    taskStatus = task.getfStatus();
                    createTime = task.getfCreateTime();
                }

                component.put(Dict.STATUS, taskStatus);
                component.put(Dict.TIME, createTime);
                componentList.add(component);
            }

            data.put(Dict.COMPONENT_LIST, componentList);
            return new ResponseResult<>(ErrorCode.SUCCESS, data);

        } else {
            return new ResponseResult<>(retCode, resultObject.getString(Dict.RETMSG));
        }
    }

    public JSONObject getNeedRunInfo(Map paramMap) {
        String result;
        try {
            result = flowFeign.get(Dict.URL_DAG_DEPENDENCY, paramMap);
        } catch (Exception e) {
            logger.error("connect fateflow error:", e);
            return null;
        }

        if ((result == null) || 0 == result.trim().length()) {
            return null;
        }

        JSONObject resultObject = JSON.parseObject(result);
        Integer retCode = resultObject.getInteger(Dict.CODE);
        if (retCode == 0) {
            JSONObject data = resultObject.getJSONObject(Dict.DATA);
            JSONObject component_need_run = data.getJSONObject(Dict.COMPONENT_NEED_RUN);
            return component_need_run;
        }
        return null;
    }


    public ResponseResult getDagDependencies(String param) {
        //check and get parameters
        JSONObject jsonObject = JSON.parseObject(param);
        String jobId = jsonObject.getString(Dict.JOBID);
        String role = jsonObject.getString(Dict.ROLE);
        String partyId = jsonObject.getString(Dict.PARTY_ID);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(Dict.JOBID, jobId);
        paramMap.put(Dict.ROLE, role);
        paramMap.put(Dict.PARTY_ID, partyId);

        String result;
        try {
            result = flowFeign.get(Dict.URL_DAG_DEPENDENCY, paramMap);
        } catch (Exception e) {
            logger.error("connect fateflow error:", e);
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_CONNECTION);
        }

        if ((result == null) || 0 == result.trim().length()) {
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_NULL_RESULT);
        }

        JSONObject resultObject = JSON.parseObject(result);
        Integer retCode = resultObject.getInteger(Dict.CODE);
        if (retCode == null) {
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_WRONG_RESULT);
        }

        if (retCode == 0) {
            JSONObject data = resultObject.getJSONObject(Dict.DATA);
            JSONArray jsonArray = data.getJSONArray(Dict.COMPONENT_LIST);
            ArrayList<Map<String, Object>> componentList = new ArrayList<>();


            for (int i = 0; i < jsonArray.size(); i++) {
                String componentName = jsonArray.getString(i);
                HashMap<String, Object> component = new HashMap<>();
                component.put(Dict.COMPONENT_NAME, componentName);
                Map<String, Object> taskDetail = taskManagerService.findTaskDetail(jobId, role, partyId, (String) componentName);
                String taskStatus = null;
                Long createTime = null;
                if (taskDetail != null) {
                    taskStatus = taskDetail.get("status") == null ? null : String.valueOf(taskDetail.get("status"));
                    createTime = taskDetail.get("time") == null ? null : Long.valueOf(taskDetail.get("time").toString());
                }

                component.put(Dict.STATUS, taskStatus);
                component.put(Dict.TIME, createTime);
                componentList.add(component);
            }
            data.put(Dict.COMPONENT_LIST, componentList);
            return new ResponseResult<>(ErrorCode.SUCCESS, data);

        } else {
            return new ResponseResult<>(retCode, resultObject.getString(Dict.RETMSG));
        }
    }

    @RequestMapping(value = "/tracking/component/output/model", method = RequestMethod.POST)
    @ResponseBody
    public ResponseResult getModel(@Valid @RequestBody ComponentQueryDTO componentQueryDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            FieldError errors = bindingResult.getFieldError();
            return new ResponseResult<>(ErrorCode.ERROR_PARAMETER, errors.getDefaultMessage());
        }
        Preconditions.checkArgument(LogFileService.checkPathParameters(componentQueryDTO.getJob_id(), componentQueryDTO.getRole(), componentQueryDTO.getParty_id(), componentQueryDTO.getComponent_name()));
        Map<String, Object> reqMap = parseQueryParam(componentQueryDTO);
        String result;
        try {
            result = flowFeign.get(Dict.URL_OUTPUT_MODEL, reqMap);
        } catch (Exception e) {
            logger.error("connect fateflow error:", e);
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_CONNECTION);
        }

        JSONObject resultObject = JSON.parseObject(result);
        Integer retCode = resultObject.getInteger(Dict.CODE);
        Object o = resultObject.get(Dict.DATA);
        if (retCode != 0) {
            return ResponseUtil.buildResponse(null, null);
        }
        return new ResponseResult(ErrorCode.SUCCESS.getCode(), resultObject.get(Dict.DATA));
    }

    @RequestMapping(value = "/tracking/component/output/data", method = RequestMethod.POST)
    @ResponseBody
    public ResponseResult getData(@Valid @RequestBody ComponentQueryDTO componentQueryDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            FieldError errors = bindingResult.getFieldError();
            return new ResponseResult<>(ErrorCode.ERROR_PARAMETER, errors.getDefaultMessage());
        }
        Preconditions.checkArgument(LogFileService.checkPathParameters(componentQueryDTO.getJob_id(), componentQueryDTO.getRole(), componentQueryDTO.getParty_id(), componentQueryDTO.getComponent_name()));

        Map<String, Object> reqMap = parseQueryParam(componentQueryDTO);
        String result;
        try {
            result = flowFeign.get(Dict.URL_OUTPUT_DATA, reqMap);
        } catch (Exception e) {
            logger.error("connect fateflow error:", e);
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_CONNECTION);
        }

        if ((result == null) || 0 == result.trim().length()) {
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_NULL_RESULT);
        }
        JSONObject object = JSON.parseObject(result);
        if (object.isEmpty()) {
            return new ResponseResult(ErrorCode.SUCCESS, object);
        }
        Set<String> set = object.keySet();
        String[] keys = set.toArray(new String[0]);
        JSONArray outputDataArray = new JSONArray();
        if (keys.length > 0) {
            String keyName = keys[0];
            outputDataArray = object.getJSONArray(keyName);
        }

        JSONObject outputData = (JSONObject) outputDataArray.get(0);

        JSONArray jsonArray = outputData.getJSONArray("data");
        int count = 0;
        if (jsonArray != null) {
            count = jsonArray.size();
        }
        outputData.put(Dict.DATA_COUNT, count);
        outputDataArray.clear();
        outputDataArray.add(outputData);
        object.clear();
        object.put(Dict.OUTPUT_DATA, outputDataArray);
        return new ResponseResult(ErrorCode.SUCCESS, object);
    }

    @RequestMapping(value = "/tracking/component/metric_data/batch", method = RequestMethod.POST)
    @ResponseBody
    public ResponseResult getBatchMetricInfo(@Valid @RequestBody BatchMetricDTO batchMetricDTO, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            FieldError errors = bindingResult.getFieldError();
            return new ResponseResult(ErrorCode.ERROR_PARAMETER, errors.getDefaultMessage());
        }
        Map<String, String[]> metrics = batchMetricDTO.getMetrics();
        Set<Map.Entry<String, String[]>> entries = metrics.entrySet();
        for (Map.Entry<String, String[]> entry : entries) {
            String key = entry.getKey();
            Preconditions.checkArgument(LogFileService.checkPathParameters(key));
            String[] values = entry.getValue();
            for (String value : values) {
                Preconditions.checkArgument(LogFileService.checkPathParameters(value));
            }
        }

        JSONObject batchMetricInfo = jobDetailService.getBatchMetricInfo(batchMetricDTO);
        if (batchMetricInfo == null) {
            return new ResponseResult<>(ErrorCode.FATEFLOW_ERROR_CONNECTION);
        }
        return new ResponseResult<>(ErrorCode.SUCCESS, batchMetricInfo);
    }

    @RequestMapping(value = "/server/fateflow/info", method = RequestMethod.POST)
    @ResponseBody
    public ResponseResult flowInfo() {
        String result = flowFeign.get(Dict.URL_FLOW_INFO, Collections.EMPTY_MAP);
        JSONObject jsonObject = JSON.parseObject(result);
        Integer retCode = jsonObject.getInteger(Dict.CODE);
        if (retCode != 0) {
            return new ResponseResult<>(retCode, jsonObject.getString(Dict.RETMSG));
        }
        return new ResponseResult(ErrorCode.SUCCESS.getCode(), jsonObject.get(Dict.DATA));
    }

    private Map<String, Object> parseQueryParam(ComponentQueryDTO componentQueryDTO) {
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put(Dict.JOBID, componentQueryDTO.getJob_id());
        reqMap.put(Dict.ROLE, componentQueryDTO.getRole());
        reqMap.put(Dict.PARTY_ID, componentQueryDTO.getParty_id());
        reqMap.put(Dict.TASK_NAME, componentQueryDTO.getComponent_name());
        return reqMap;
    }
}
