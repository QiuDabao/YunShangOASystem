package com.atguigu.process.service.impl;

import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.process.ProcessType;
import com.atguigu.process.mapper.OaProcessTypeMapper;
import com.atguigu.process.service.OaProcessTemplateService;
import com.atguigu.process.service.OaProcessTypeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 审批类型 服务实现类
 * </p>
 *
 * @author atguigu
 * @since 2023-04-18
 */
@Service
public class OaProcessTypeServiceImpl extends ServiceImpl<OaProcessTypeMapper, ProcessType> implements OaProcessTypeService {

    @Resource
    private OaProcessTemplateService processTemplateService;


    @Override
    public List<ProcessType> findProcessType() {
        //查询所有审批分类
        List<ProcessType> processTypeList = baseMapper.selectList(null);
        //得到每个审批分类
        for(ProcessType processType:processTypeList){
            //审批分类id
            Long id = processType.getId();
            //根据审批id查询对应审批模板
            LambdaQueryWrapper<ProcessTemplate> wrapper = new LambdaQueryWrapper<ProcessTemplate>().eq(ProcessTemplate::getProcessTypeId, id);
            List<ProcessTemplate> processTemplateList = processTemplateService.list(wrapper);
            //封装
            processType.setProcessTemplateList(processTemplateList);
        }
        return processTypeList;
    }
}
