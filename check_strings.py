#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import xml.etree.ElementTree as ET
import os
import sys

def parse_strings_file(filepath):
    """解析strings.xml文件，返回键值对字典和重复项"""
    strings_dict = {}
    duplicates = []
    
    if not os.path.exists(filepath):
        print(f"❌ 文件不存在: {filepath}")
        return strings_dict, duplicates
    
    try:
        tree = ET.parse(filepath)
        root = tree.getroot()
        
        for string_elem in root.findall('string'):
            name = string_elem.get('name')
            if name:
                if name in strings_dict:
                    duplicates.append(name)
                strings_dict[name] = string_elem.text or ""
                
    except Exception as e:
        print(f"❌ 解析文件失败 {filepath}: {e}")
        
    return strings_dict, duplicates

def main():
    files = {
        '中文': 'app/src/main/res/values/strings.xml',
        '英文': 'app/src/main/res/values-en/strings.xml', 
        # '西班牙语': 'app/src/main/res/values-es/strings.xml'
    }
    
    # 检查是否使用简化模式
    simple_mode = len(sys.argv) > 1 and sys.argv[1] == "--simple"
    
    print("📊 Android Strings.xml 检查结果")
    print("=" * 50)
    
    all_data = {}
    all_keys = set()
    all_duplicates = {}
    total_duplicates = 0
    
    # 解析所有文件
    for lang, filepath in files.items():
        print(f"正在解析 {lang}: {filepath}")
        data, duplicates = parse_strings_file(filepath)
        all_data[lang] = data
        all_keys.update(data.keys())
        all_duplicates[lang] = duplicates
        total_duplicates += len(duplicates)
        
        print(f"{lang}: {len(data)} 个字符串, {len(duplicates)} 个重复项")
    
    print(f"\n📈 总计: {len(all_keys)} 个唯一字符串键")
    print(f"🔄 总重复项: {total_duplicates}")
    
    if not simple_mode:
        # 详细输出重复项
        print("\n" + "=" * 50)
        print("🔄 重复项详情:")
        for lang, duplicates in all_duplicates.items():
            if duplicates:
                print(f"\n❌ {lang} 重复项 ({len(duplicates)}个):")
                for dup in duplicates:
                    print(f"   - {dup}")
            else:
                print(f"\n✅ {lang}: 无重复项")
        
        # 详细输出缺失项
        print("\n" + "=" * 50)
        print("❌ 缺失项详情:")
    
    total_missing = 0
    for lang, data in all_data.items():
        missing = sorted(all_keys - set(data.keys()))
        total_missing += len(missing)
        if missing:
            if simple_mode:
                print(f"❌ {lang}: 缺少 {len(missing)} 个字符串")
            else:
                print(f"\n❌ {lang} 缺失项 ({len(missing)}个):")
                # 按类别分组显示，更容易阅读
                categories = {}
                for key in missing:
                    if '_' in key:
                        prefix = key.split('_')[0]
                        if prefix not in categories:
                            categories[prefix] = []
                        categories[prefix].append(key)
                    else:
                        if 'other' not in categories:
                            categories['other'] = []
                        categories['other'].append(key)
                
                for category, keys in sorted(categories.items()):
                    print(f"   [{category}]:")
                    for key in keys[:10]:  # 只显示前10个，避免输出过长
                        print(f"     - {key}")
                    if len(keys) > 10:
                        print(f"     ... 还有 {len(keys) - 10} 个")
        else:
            if simple_mode:
                print(f"✅ {lang}: 完整")
            else:
                print(f"\n✅ {lang}: 完整")
    
    print(f"\n🎯 总缺失项: {total_missing}")
    
    if total_duplicates == 0 and total_missing == 0:
        print("\n🎉 所有文件都已完整且无重复项！")
    else:
        print(f"\n⚠️  还需修复: {total_duplicates} 个重复项 + {total_missing} 个缺失项")

if __name__ == "__main__":
    main()