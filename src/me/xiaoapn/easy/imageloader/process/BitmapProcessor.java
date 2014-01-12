/*
 * Copyright 2013 Peng fei Pan
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xiaoapn.easy.imageloader.process;

import me.xiaoapn.easy.imageloader.task.ImageViewAware;
import me.xiaoapn.easy.imageloader.util.ImageSize;
import android.graphics.Bitmap;

/**
 * 位图处理器
 */
public interface BitmapProcessor {
	/**
	 * 获取标识，用来组装缓存位图时用的请求ID
	 * @return
	 */
	public String getTag();
	
	/**
	 * 处理
	 * @param bitmap
	 * @param imageViewAware
	 * @param targetSize
	 * @return
	 */
	public Bitmap process(Bitmap bitmap, ImageViewAware imageViewAware, ImageSize targetSize);
	
	/**
	 * 拷贝
	 * @return
	 */
	public BitmapProcessor copy();
}
