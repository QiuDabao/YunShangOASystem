package com.atguigu.process.service;

import com.atguigu.model.process.ProcessRecord;
import com.baomidou.mybatisplus.extension.service.IService;

public interface OaProcessRecordService extends IService<ProcessRecord> {
    //添加审批记录
    void record(Long processId,Integer status,String description);
}
