package com.example.aiteacher.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aiteacher.Entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
