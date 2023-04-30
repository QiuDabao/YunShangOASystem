package com.atguigu.process.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.auth.service.SysUserService;
import com.atguigu.model.process.Process;
import com.atguigu.model.process.ProcessRecord;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.mapper.OaProcessMapper;
import com.atguigu.process.service.OaProcessRecordService;
import com.atguigu.process.service.OaProcessService;
import com.atguigu.process.service.OaProcessTemplateService;
import com.atguigu.security.custom.LoginUserInfoHelper;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
import com.atguigu.vo.process.ProcessQueryVo;
import com.atguigu.vo.process.ProcessVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.activiti.bpmn.model.*;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricTaskInstanceQuery;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

/**
 * <p>
 * 审批类型 服务实现类
 * </p>
 *
 * @author atguigu
 * @since 2023-04-19
 */
@Service
public class OaProcessServiceImpl extends ServiceImpl<OaProcessMapper,Process> implements OaProcessService {

    @Override
    public IPage<ProcessVo> selectPage(Page<ProcessVo> pageParam, ProcessQueryVo processQueryVo) {
        IPage<ProcessVo> pageModel = baseMapper.selectPage(pageParam,processQueryVo);
        return pageModel;
    }

    @Resource
    private RepositoryService repositoryService;
    @Override
    public void deployByZip(String deployPath) {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(deployPath);
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        //部署
        Deployment deployment = repositoryService.createDeployment().addZipInputStream(zipInputStream).deploy();
        System.out.println(deployment.getId());
        System.out.println(deployment.getName());
    }

    @Resource
    private SysUserService sysUserService;

    @Resource
    private OaProcessTemplateService processTemplateService;

    @Resource
    private RuntimeService runtimeService;
    @Resource
    private OaProcessRecordService processRecordService;

    //启动流程实例
    @Override
    public void startUp(ProcessFormVo processFormVo) {
        //根据用户id获取用户信息
        SysUser sysUser = sysUserService.getById(LoginUserInfoHelper.getUserId());
        //根据审批模板id查询模板信息
        ProcessTemplate processTemplate = processTemplateService.getById(processFormVo.getProcessTemplateId());
        //保存提交的审批信息到业务表(oa_process
        Process process = new Process();
        BeanUtils.copyProperties(processFormVo,process);//复制值到一个空对象
        process.setStatus(1);
        String workNo = System.currentTimeMillis() + "";
        process.setProcessCode(workNo);
        process.setUserId(LoginUserInfoHelper.getUserId());
        process.setFormValues(processFormVo.getFormValues());
        process.setTitle(sysUser.getName() + "发起" + processTemplate.getName() + "申请");
        baseMapper.insert(process);
        //启动流程实例 - RuntimeService
        String processDefinitionKey = processTemplate.getProcessDefinitionKey();//流程定义key
        String businessKey = String.valueOf(process.getId());//业务key
        String formValues = processFormVo.getFormValues();//流程参数->map集合

        JSONObject jsonObject = JSON.parseObject(formValues);
        JSONObject formData = jsonObject.getJSONObject("formData");

        Map<String, Object> map = new HashMap<>();
        for(Map.Entry<String,Object> entry:formData.entrySet()){
            map.put(entry.getKey(),entry.getValue());
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("data",map);

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processDefinitionKey, businessKey, variables);
        //查询下一个审批人(可能会有多个
        List<Task> tasks = this.getCurrentTaskList(processInstance.getId());
        List<String> nameList = new ArrayList<>();
        for(Task task:tasks){
            String assigneeName = task.getAssignee();
            SysUser user = sysUserService.getUserByUserName(assigneeName);
            String name = user.getName();
            nameList.add(name);
            //todo 推送消息
        }
        process.setProcessInstanceId(processInstance.getId());
        process.setDescription("等待"+nameList+"审批");
        //业务和流程关联
        baseMapper.updateById(process);

        //记录操作审批信息记录
        processRecordService.record(process.getId(),1,"发起申请");
    }

    @Override
    public IPage<ProcessVo> findPending(Page<Process> pageParam) {
        //根据当前登录的用户名称,查询代办任务列表
        TaskQuery query = taskService.createTaskQuery()
                .taskAssignee(LoginUserInfoHelper.getUsername())
                .orderByTaskCreateTime()
                .desc();
        int begin = (int)((pageParam.getCurrent()-1)*pageParam.getSize());
        int size = (int)(pageParam.getSize());
        List<Task> taskList = query.listPage(begin, size);//第一个参数:开始位置 第二个参数:每页显示记录数

        //封装返回list集合数据到List<ProcessVo>中去
        List<ProcessVo> processVoList = new ArrayList<>();
        for(Task task : taskList){
            //流程实例id
            String processInstanceId = task.getProcessInstanceId();
            //实例对象
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                                                            .processInstanceId(processInstanceId)
                                                            .singleResult();
            //实例对象中的业务key--processId
            String businessKey = processInstance.getBusinessKey();
            if (businessKey == null) {
                continue;
            }
            //根据业务key获取Process对象
            long processId = Long.parseLong(businessKey);
            Process process = baseMapper.selectById(processId);
            //将Process对象复制到ProcessVo中
            ProcessVo processVo = new ProcessVo();
            BeanUtils.copyProperties(process,processVo);
            processVo.setTaskId(task.getId());
            processVoList.add(processVo);
        }
        //封装IPage对象
        IPage<ProcessVo> page = new Page<>(pageParam.getCurrent(),
                                            pageParam.getSize(),
                                            query.count());//当前页,记录数,总记录数
        page.setRecords(processVoList);
        return page;
    }

    @Override
    public Map<String, Object> show(Long id) {
        //根据流程id获取流程信息和流程记录信息
        Process process = baseMapper.selectById(id);
        List<ProcessRecord> processRecordList = processRecordService.list(new LambdaQueryWrapper<ProcessRecord>().eq(ProcessRecord::getProcessId, id));
        //根据模板id查询模板信息
        ProcessTemplate processTemplate = processTemplateService.getById(process.getProcessTemplateId());
        //审批
        boolean isApprove = false;
        List<Task> taskList = this.getCurrentTaskList(process.getProcessInstanceId());
        for(Task task:taskList){
            //审批人是否是当前用户
            if(task.getAssignee().equals(LoginUserInfoHelper.getUsername())){
                isApprove=true;
            }
        }
        //封装
        HashMap<String, Object> map = new HashMap<>();
        map.put("process",process);
        map.put("processRecordList",processRecordList);
        map.put("processTemplate",processTemplate);
        map.put("isApprove",isApprove);
        return map;
    }

    @Override
    public void approve(ApprovalVo approvalVo) {
        //获取任务id->流程变量
        String taskId = approvalVo.getTaskId();
        Object variables = taskService.getVariables(taskId);
        //审批
        if (approvalVo.getStatus() == 1) {
            HashMap<String, Object> variable = new HashMap<>();
            taskService.complete(taskId,variable);
        }else {
            this.endTask(taskId);
        }
        String description = approvalVo.getStatus()==1?"通过":"驳回";
        processRecordService.record(approvalVo.getProcessId(),approvalVo.getStatus(),description);
        //查询下一个审批人,更新流程表记录
        Process process = baseMapper.selectById(approvalVo.getProcessId());
        List<Task> taskList = this.getCurrentTaskList(process.getProcessInstanceId());
        if (!CollectionUtils.isEmpty(taskList)) {
            List<String> assignList = new ArrayList<>();
            for(Task task:taskList){
                String assignee = task.getAssignee();
                SysUser sysUser = sysUserService.getUserByUserName(assignee);
                assignList.add(sysUser.getName());
            }
            //更新流程信息
            process.setDescription("等待审批");
            process.setStatus(1);
        }else {
            if (approvalVo.getStatus().intValue() == 1) {
                process.setDescription("审批完成");
                process.setStatus(2);
            }else {
                process.setDescription("审批驳回");
                process.setStatus(-1);
            }
        }
        baseMapper.updateById(process);
    }

    @Resource
    private HistoryService historyService;
    //已处理任务
    @Override
    public IPage<ProcessVo> findProcessed(Page<Process> pageParam) {
        //查询遍历并封装成Ipage
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(LoginUserInfoHelper.getUsername())
                .finished()
                .orderByTaskCreateTime().desc();

        int begin = (int)((pageParam.getCurrent()-1)*pageParam.getSize());
        int size = (int)(pageParam.getSize());
        List<HistoricTaskInstance> list = query.listPage(begin, size);//需要开始位置和每页显示记录数
        long totalCount = query.count();
        List<ProcessVo> processVoList = new ArrayList<>();
        for(HistoricTaskInstance item:list){
            String processInstanceId = item.getProcessInstanceId();
            //根据流程实例id获取process信息
            LambdaQueryWrapper<Process> wrapper = new LambdaQueryWrapper<Process>().eq(Process::getProcessInstanceId, processInstanceId);
            Process process = baseMapper.selectOne(wrapper);
            //process->processVo
            ProcessVo processVo = new ProcessVo();
            BeanUtils.copyProperties(process,processVo);
            processVoList.add(processVo);
        }
        IPage<ProcessVo> pageModel = new Page<>(pageParam.getCurrent(), pageParam.getSize(),totalCount);
        pageModel.setRecords(processVoList);
        return pageModel;
    }

    //已发起
    @Override
    public IPage<ProcessVo> findStarted(Page<ProcessVo> pageParam) {
        ProcessQueryVo processQueryVo = new ProcessQueryVo();
        processQueryVo.setUserId(LoginUserInfoHelper.getUserId());
        IPage<ProcessVo> pageModel = baseMapper.selectPage(pageParam, processQueryVo);
        return pageModel;
    }

    //结束任务
    private void endTask(String taskId) {
        //获取任务对象
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        //获取流程定义模型BpmnModel
        BpmnModel bpmnModel = repositoryService.getBpmnModel(task.getProcessDefinitionId());
        //获取结束流向节点和当前流向节点
        List<EndEvent> endEvents = bpmnModel.getMainProcess().findFlowElementsOfType(EndEvent.class);
        if (CollectionUtils.isEmpty(endEvents)) {
            return;
        }
        FlowNode endFlowNode = endEvents.get(0);
        FlowNode currentFlowNode = (FlowNode) bpmnModel.getMainProcess().getFlowElement(task.getTaskDefinitionKey());
        //临时保存当前活动的原始方向
        List originalSequenceFlowList = new ArrayList<>();
        originalSequenceFlowList.addAll(currentFlowNode.getOutgoingFlows());
        //清理当前流向
        currentFlowNode.getOutgoingFlows().clear();
        //创建新流向
        SequenceFlow newSequenceFlow = new SequenceFlow();
        newSequenceFlow.setId("newSequenceFlow");
        newSequenceFlow.setSourceFlowElement(currentFlowNode);
        newSequenceFlow.setTargetFlowElement(endFlowNode);
        //当前节点指向新流向
        List newSequenceFlowList = new ArrayList<>();
        newSequenceFlowList.add(newSequenceFlow);
        currentFlowNode.setOutgoingFlows(newSequenceFlowList);
        //完成任务
        taskService.complete(taskId);
    }

    @Resource
    private TaskService taskService;
    //当前任务列表
    private List<Task> getCurrentTaskList(String id) {
        return taskService.createTaskQuery().processInstanceId(id).list();
    }
}
